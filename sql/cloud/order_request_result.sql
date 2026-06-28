USE tikectsystem_program_0;

CREATE TABLE IF NOT EXISTS `order_request_result_0` (
  `id` bigint NOT NULL COMMENT 'Primary key',
  `request_id` varchar(64) NOT NULL COMMENT 'Order request idempotent id',
  `order_number` bigint NOT NULL COMMENT 'Order number',
  `program_id` bigint NOT NULL COMMENT 'Program id',
  `user_id` bigint NOT NULL COMMENT 'User id',
  `result_status` varchar(32) NOT NULL COMMENT 'PROCESSING/RESERVED/ORDER_CREATED/FAILED/CANCELLED/EXPIRED',
  `reservation_json` text DEFAULT NULL COMMENT 'Redis reservation snapshot',
  `fail_code` varchar(64) DEFAULT NULL COMMENT 'Failure code',
  `fail_message` varchar(512) DEFAULT NULL COMMENT 'Failure message',
  `expire_time` datetime DEFAULT NULL COMMENT 'Reservation expire time',
  `create_time` datetime DEFAULT NULL COMMENT 'Create time',
  `edit_time` datetime DEFAULT NULL COMMENT 'Edit time',
  `status` tinyint DEFAULT 1 COMMENT '1 normal 0 deleted',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_number` (`order_number`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_program_status` (`program_id`, `result_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Order request result';

CREATE TABLE IF NOT EXISTS `order_request_result_1` LIKE `order_request_result_0`;
