-- Existing bounties and service listings have no publication request key.
SET @bounty_publish_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_post' AND COLUMN_NAME = 'publish_request_id'
), 'DO 0', 'ALTER TABLE trade_post ADD COLUMN publish_request_id VARCHAR(36) NULL');
PREPARE bounty_publish_stmt FROM @bounty_publish_sql;
EXECUTE bounty_publish_stmt;
DEALLOCATE PREPARE bounty_publish_stmt;

SET @bounty_publish_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_post' AND INDEX_NAME = 'uk_trade_post_publish_request'
), 'DO 0', 'ALTER TABLE trade_post ADD CONSTRAINT uk_trade_post_publish_request UNIQUE (publisher_id, publish_request_id)');
PREPARE bounty_publish_stmt FROM @bounty_publish_sql;
EXECUTE bounty_publish_stmt;
DEALLOCATE PREPARE bounty_publish_stmt;
