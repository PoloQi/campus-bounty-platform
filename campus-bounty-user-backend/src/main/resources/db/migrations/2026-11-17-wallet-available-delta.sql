-- wallet_log.amount is the available balance delta. The freeze already debited it.
-- Preserve the settlement amount in the remark before normalizing the legacy rows.
UPDATE wallet_log
SET remark = CONCAT('订单托管款已结算 ¥', ABS(amount)),
    amount = 0.00
WHERE type = 'PAY'
  AND amount < 0.00;
