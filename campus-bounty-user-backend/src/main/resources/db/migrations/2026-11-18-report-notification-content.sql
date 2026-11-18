-- Widen report notifications to retain the processing administrator and the full opinion.
ALTER TABLE t_notification MODIFY COLUMN content VARCHAR(300) NOT NULL;
