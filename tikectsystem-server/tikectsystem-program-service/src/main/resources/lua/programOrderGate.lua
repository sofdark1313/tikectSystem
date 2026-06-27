local type = tonumber(ARGV[1])
local request_id = ARGV[2]
local order_number = ARGV[3]
local gate_ttl_seconds = tonumber(ARGV[4])
local inflight_limit = tonumber(ARGV[5])

local request_key = KEYS[1]
local inflight_key = KEYS[2]

local exists_order_number = redis.call('get', request_key)
if exists_order_number then
    return string.format('{"%s": %d, "%s": "%s"}', 'code', 0, 'orderNumber', exists_order_number)
end

if type == 1 then
    for i = 3, #KEYS do
        local locked = redis.call('set', KEYS[i], request_id, 'NX', 'EX', gate_ttl_seconds)
        if not locked then
            for j = 3, i - 1 do
                redis.call('del', KEYS[j])
            end
            return string.format('{"%s": %d}', 'code', 40004)
        end
    end
else
    local inflight = redis.call('incr', inflight_key)
    redis.call('expire', inflight_key, gate_ttl_seconds)
    if inflight > inflight_limit then
        redis.call('decr', inflight_key)
        return string.format('{"%s": %d}', 'code', 40011)
    end
end

redis.call('set', request_key, order_number, 'EX', gate_ttl_seconds)
return string.format('{"%s": %d, "%s": "%s"}', 'code', 0, 'orderNumber', order_number)
