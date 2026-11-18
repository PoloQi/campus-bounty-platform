-- Existing orders have no purchase request key and continue to allow NULL.
SET @trade_wallet_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND COLUMN_NAME = 'purchase_request_id'
), 'DO 0', 'ALTER TABLE trade_order ADD COLUMN purchase_request_id VARCHAR(36) NULL');
PREPARE trade_wallet_stmt FROM @trade_wallet_sql;
EXECUTE trade_wallet_stmt;
DEALLOCATE PREPARE trade_wallet_stmt;

SET @trade_wallet_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'trade_order' AND INDEX_NAME = 'uk_trade_order_purchase_request'
), 'DO 0', 'ALTER TABLE trade_order ADD CONSTRAINT uk_trade_order_purchase_request UNIQUE (post_id, buyer_id, purchase_request_id)');
PREPARE trade_wallet_stmt FROM @trade_wallet_sql;
EXECUTE trade_wallet_stmt;
DEALLOCATE PREPARE trade_wallet_stmt;

-- Wallet totals accumulate many individually bounded transactions.
SET @trade_wallet_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet'
        AND COLUMN_NAME IN ('balance', 'frozen') AND NUMERIC_PRECISION < 30
), 'ALTER TABLE wallet MODIFY balance DECIMAL(30, 2) NOT NULL DEFAULT 0.00, MODIFY frozen DECIMAL(30, 2) NOT NULL DEFAULT 0.00', 'DO 0');
PREPARE trade_wallet_stmt FROM @trade_wallet_sql;
EXECUTE trade_wallet_stmt;
DEALLOCATE PREPARE trade_wallet_stmt;

SET @trade_wallet_sql = IF(EXISTS (
    SELECT 1 FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'wallet_log'
        AND COLUMN_NAME = 'balance_after' AND NUMERIC_PRECISION < 30
), 'ALTER TABLE wallet_log MODIFY balance_after DECIMAL(30, 2) NOT NULL', 'DO 0');
PREPARE trade_wallet_stmt FROM @trade_wallet_sql;
EXECUTE trade_wallet_stmt;
DEALLOCATE PREPARE trade_wallet_stmt;
