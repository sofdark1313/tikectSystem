ALTER TABLE `d_message_producer_record`
    ADD COLUMN `message_key` varchar(128) DEFAULT NULL COMMENT 'Kafka original message key' AFTER `message_id`;
