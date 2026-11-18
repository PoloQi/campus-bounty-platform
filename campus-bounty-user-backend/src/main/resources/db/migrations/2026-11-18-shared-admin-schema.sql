-- Run after schema.sql on an existing MySQL 8 database. Existing shared-schema values take priority.
SELECT GROUP_CONCAT(CONCAT('ADD COLUMN ', desired.definition) ORDER BY desired.position SEPARATOR ', ')
INTO @report_columns
FROM (
    SELECT 1 AS position, 'handler_id' AS name, 'handler_id BIGINT NULL' AS definition
    UNION ALL SELECT 2, 'handled_at', 'handled_at DATETIME NULL'
    UNION ALL SELECT 3, 'result', 'result VARCHAR(16) NULL'
    UNION ALL SELECT 4, 'handle_comment', 'handle_comment VARCHAR(500) NULL'
) AS desired
LEFT JOIN information_schema.columns AS existing
    ON existing.table_schema = DATABASE() AND existing.table_name = 't_report'
    AND existing.column_name = desired.name
WHERE existing.column_name IS NULL;

SET @sql = IF(@report_columns IS NULL, 'DO 0', CONCAT('ALTER TABLE t_report ', @report_columns));
PREPARE upgrade_admin_schema FROM @sql;
EXECUTE upgrade_admin_schema;
DEALLOCATE PREPARE upgrade_admin_schema;

START TRANSACTION;

SET @sql = IF(EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 't_report_resolution'
), '
    UPDATE t_report AS r JOIN t_report_resolution AS x ON x.report_id = r.id
    SET r.handler_id = COALESCE(r.handler_id, x.admin_id),
        r.handled_at = COALESCE(r.handled_at, x.handled_at),
        r.result = COALESCE(r.result, x.result),
        r.handle_comment = COALESCE(r.handle_comment, x.comment)
    WHERE r.status = ''HANDLED''
', 'DO 0');
PREPARE upgrade_admin_schema FROM @sql;
EXECUTE upgrade_admin_schema;
DEALLOCATE PREPARE upgrade_admin_schema;

-- Compare occurrence counts so reruns preserve genuine duplicate events without copying them again.
SET @sql = IF(EXISTS (
    SELECT 1 FROM information_schema.tables
    WHERE table_schema = DATABASE() AND table_name = 'admin_log'
), '
    INSERT INTO t_admin_log (admin_id, action, target_type, target_id, detail, created_at)
    SELECT source_log.admin_id, source_log.action, source_log.target_type, source_log.target_id,
           source_log.detail, source_log.created_at
    FROM (
        SELECT id, admin_id, action, target_type, target_id, LEFT(details, 500) AS detail, created_at,
               ROW_NUMBER() OVER (
                   PARTITION BY admin_id, action, target_type, target_id, CAST(LEFT(details, 500) AS BINARY), created_at
                   ORDER BY id
               ) AS occurrence
        FROM admin_log
    ) AS source_log
    LEFT JOIN (
        SELECT admin_id, action, target_type, target_id, CAST(detail AS BINARY) AS detail_key,
               created_at, COUNT(*) AS occurrences
        FROM t_admin_log
        GROUP BY admin_id, action, target_type, target_id, detail_key, created_at
    ) AS copied
        ON copied.admin_id = source_log.admin_id AND copied.action = source_log.action
        AND copied.target_type = source_log.target_type AND copied.target_id = source_log.target_id
        AND copied.detail_key = CAST(source_log.detail AS BINARY)
        AND copied.created_at = source_log.created_at
    WHERE source_log.occurrence > COALESCE(copied.occurrences, 0)
    ORDER BY source_log.id
', 'DO 0');
PREPARE upgrade_admin_schema FROM @sql;
EXECUTE upgrade_admin_schema;
DEALLOCATE PREPARE upgrade_admin_schema;

COMMIT;
