USE tikectsystem_program_0;

CREATE TABLE IF NOT EXISTS `order_request_result_0` (
  `id` bigint NOT NULL COMMENT '主键',
  `request_id` varchar(64) NOT NULL COMMENT '下单请求幂等号',
  `order_number` bigint NOT NULL COMMENT '订单编号',
  `program_id` bigint NOT NULL COMMENT '节目编号',
  `user_id` bigint NOT NULL COMMENT '用户编号',
  `result_status` varchar(32) NOT NULL COMMENT 'PROCESSING/RESERVED/ORDER_CREATED/FAILED/CANCELLED/EXPIRED',
  `reservation_json` text DEFAULT NULL COMMENT 'Redis 锁座快照 JSON',
  `fail_code` varchar(64) DEFAULT NULL COMMENT '失败编码',
  `fail_message` varchar(512) DEFAULT NULL COMMENT '失败原因',
  `expire_time` datetime DEFAULT NULL COMMENT '锁座过期时间',
  `create_time` datetime DEFAULT NULL COMMENT '创建时间',
  `edit_time` datetime DEFAULT NULL COMMENT '编辑时间',
  `status` tinyint DEFAULT 1 COMMENT '1 正常 0 删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_number` (`order_number`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_program_status` (`program_id`, `result_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='异步下单请求结果';

CREATE TABLE IF NOT EXISTS `order_request_result_1` LIKE `order_request_result_0`;

USE tikectsystem_program_1;

CREATE TABLE IF NOT EXISTS `order_request_result_0` LIKE `tikectsystem_program_0`.`order_request_result_0`;

CREATE TABLE IF NOT EXISTS `order_request_result_1` LIKE `tikectsystem_program_0`.`order_request_result_0`;
