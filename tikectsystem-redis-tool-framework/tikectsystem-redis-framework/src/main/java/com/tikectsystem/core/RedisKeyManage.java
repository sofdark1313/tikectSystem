package com.tikectsystem.core;

import lombok.Getter;

/**
 * Redis key registry.
 */
@Getter
public enum RedisKeyManage {
    Key("key", "test key", "test value", "k"),
    Key2("key:%s", "test placeholder key", "test value", "k"),

    USER_LOGIN("user_login_%s_%s", "user login", "user login value", "k"),
    PRODUCT_STOCK("product_stock:%s", "product stock", "stock value", "k"),
    DISTRIBUTED_DATACENTER_ID("distributed_datacenter_id:%s", "distributed datacenter id", "datacenter id", "lk"),

    ALL_RULE_HASH("all_rule_hash", "all rule hash", "all rules", "k"),
    RULE("rule", "rule key", "rule value", "k"),
    RULE_LIMIT("rule_limit_%s", "rule limit key", "rule limit value", "k"),
    Z_SET_RULE_STAT("z_set_rule_stat_%s", "rule zset", "zset value", "k"),
    DEPTH_RULE("depth_rule", "depth rule key", "depth rule value", "k"),
    DEPTH_RULE_LIMIT("depth_rule_limit_%s_%s", "depth rule limit key", "depth rule limit value", "k"),

    API_STAT_CONTROLLER_METHOD_DATA("api_stat_controller_method_data:%s", "controller method data key", "controller method data", "k"),
    API_STAT_SERVICE_METHOD_DATA("api_stat_service_method_data:%s", "service method data key", "service method data", "k"),
    API_STAT_DAO_METHOD_DATA("api_stat_dao_method_data:%s", "dao method data key", "dao method data", "k"),
    API_STAT_METHOD_HIERARCHY("api_stat_method_Hierarchy:%s", "method hierarchy key", "method hierarchy", "k"),
    API_STAT_METHOD_DETAIL("api_stat_method_detail:%s", "method detail key", "method detail", "k"),
    API_STAT_CONTROLLER_SORTED_SET("api_stat_controller_sorted_set", "controller sorted set key", "controller sorted set", "k"),
    API_STAT_CONTROLLER_CHILDREN_SET("api_stat_controller_children_set:%s", "controller children set key", "controller children set", "k"),
    API_STAT_SERVICE_CHILDREN_SET("api_stat_service_children_set:%s", "service children set key", "service children set", "k"),

    PLATFORM_NOTICE_FLAG("platform_notice_flag", "platform notice flag key", "platform notice flag", "k"),
    CHANNEL_DATA("channel_data_%s", "channel data key", "channel data", "k"),

    PROGRAM("d_mai_program_%s", "program key", "program value", "k"),
    PROGRAM_GROUP("d_mai_program_group_%s", "program group key", "program group value", "k"),
    PROGRAM_SHOW_TIME("d_mai_program_show_time_%s", "program show time key", "program show time value", "k"),
    PROGRAM_SEAT_NO_SOLD_RESOLUTION_HASH("d_mai_program_seat_no_sold_resolution_hash_%s_%s", "program no sold seat hash key", "program no sold seat hash", "k"),
    PROGRAM_SEAT_LOCK_RESOLUTION_HASH("d_mai_program_seat_lock_resolution_hash_%s_%s", "program locked seat hash key", "program locked seat hash", "k"),
    PROGRAM_SEAT_SOLD_RESOLUTION_HASH("d_mai_program_seat_sold_resolution_hash_%s_%s", "program sold seat hash key", "program sold seat hash", "k"),
    PROGRAM_TICKET_CATEGORY_LIST("d_mai_program_ticket_category_list_%s", "program ticket category list key", "program ticket category list", "k"),
    PROGRAM_TICKET_REMAIN_NUMBER_HASH_RESOLUTION("d_mai_program_ticket_remain_number_hash_resolution_%s_%s", "program ticket remain hash key", "program ticket remain hash", "k"),
    PROGRAM_CATEGORY_HASH("d_mai_program_category_hash", "program category hash key", "program category hash", "k"),
    PROGRAM_HOME_LIST("d_mai_program_home_list_%s", "program home list key", "program home list", "k"),
    PROGRAM_HOME_LIST_ALL("d_mai_program_home_list_*", "program home list pattern", "program home list pattern", "k"),
    PROGRAM_RECORD("d_mai_program_record_%s", "program record key", "program record", "k"),
    PROGRAM_RECORD_FINISH("d_mai_program_record_finish_%s", "program finish record key", "program finish record", "k"),

    COUNTER_COUNT("d_mai_counter_count", "counter count key", "counter count", "k"),
    COUNTER_TIMESTAMP("d_mai_counter_timestamp", "counter timestamp key", "counter timestamp", "k"),
    VERIFY_CAPTCHA_ID("d_mai_verify_captcha_id_%s", "captcha id key", "captcha id", "k"),
    TICKET_USER_LIST("d_mai_ticket_user_list_%s", "ticket user list key", "ticket user list", "k"),
    ACCOUNT_ORDER_COUNT("d_mai_account_order_count_%s_%s", "account order count key", "account order count", "k"),
    ACCOUNT_ORDER_COUNT_ALL("d_mai_account_order_count_*", "account order count pattern", "account order count pattern", "k"),
    ORDER_MQ("d_mai_order_mq_%s", "mq order key", "mq order number", "k"),
    DISCARD_ORDER("d_mai_discard_order_%s", "discard order key", "discard order list", "k"),

    PROGRAM_ORDER_GATE_REQUEST("d_mai_program_order_gate_request_%s", "order gate request", "order number", "k"),
    PROGRAM_ORDER_GATE_INFLIGHT("d_mai_program_order_gate_inflight_%s_%s", "order gate inflight", "inflight count", "k"),
    PROGRAM_ORDER_GATE_SEAT("d_mai_program_order_gate_seat_%s", "order gate seat", "request id", "k"),
    PROGRAM_ORDER_RESERVATION("d_mai_program_order_reservation_%s", "order reservation", "reservation snapshot", "k"),

    LOGIN_USER_MOBILE_ERROR("d_mai_login_user_mobile_error_%s", "login mobile error key", "login mobile error count", "k"),
    LOGIN_USER_EMAIL_ERROR("d_mai_login_user_email_error_%s", "login email error key", "login email error count", "k"),
    AREA_PROVINCE_LIST("d_mai_area_province_list", "area province list key", "area province list", "k");

    private final String key;

    private final String keyIntroduce;

    private final String valueIntroduce;

    private final String author;

    RedisKeyManage(String key, String keyIntroduce, String valueIntroduce, String author) {
        this.key = key;
        this.keyIntroduce = keyIntroduce;
        this.valueIntroduce = valueIntroduce;
        this.author = author;
    }

    public static RedisKeyManage getRc(String keyCode) {
        for (RedisKeyManage re : RedisKeyManage.values()) {
            if (re.key.equals(keyCode)) {
                return re;
            }
        }
        return null;
    }
}
