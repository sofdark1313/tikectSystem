local ticket_category_list = cjson.decode(ARGV[1])
local del_seat_list = cjson.decode(ARGV[2])
local add_seat_data_list = cjson.decode(ARGV[3])

local strict_move_map = {}
for index, increase_data in ipairs(ticket_category_list) do
    local ticket_category_id = tostring(increase_data.ticketCategoryId)
    local increase_count = tonumber(increase_data.count)
    if increase_count and increase_count < 0 then
        strict_move_map[ticket_category_id] = true
    end
end

local strict_seen_seat_map = {}
for index, seat in ipairs(del_seat_list) do
    local seat_hash_key_del = seat.seatHashKeyDel
    local ticket_category_id = tostring(seat.ticketCategoryId)
    local seat_id_list = seat.seatIdList
    if strict_move_map[ticket_category_id] then
        strict_seen_seat_map[ticket_category_id] = strict_seen_seat_map[ticket_category_id] or {}
        for seat_index, seat_id in ipairs(seat_id_list) do
            local seat_id_text = tostring(seat_id)
            if strict_seen_seat_map[ticket_category_id][seat_id_text] then
                return 0
            end
            strict_seen_seat_map[ticket_category_id][seat_id_text] = true
            if redis.call('HEXISTS', seat_hash_key_del, seat_id_text) == 0 then
                return 0
            end
        end
    end
end

local moved_count_map = {}
local moved_seat_map = {}
local total_moved_count = 0

for index, seat in ipairs(del_seat_list) do
    local seat_hash_key_del = seat.seatHashKeyDel
    local ticket_category_id = tostring(seat.ticketCategoryId)
    local seat_id_list = seat.seatIdList
    moved_count_map[ticket_category_id] = moved_count_map[ticket_category_id] or 0
    moved_seat_map[ticket_category_id] = moved_seat_map[ticket_category_id] or {}
    for seat_index, seat_id in ipairs(seat_id_list) do
        local seat_id_text = tostring(seat_id)
        local exists_seat = redis.call('HGET', seat_hash_key_del, seat_id_text)
        if exists_seat then
            redis.call('HDEL', seat_hash_key_del, seat_id_text)
            moved_count_map[ticket_category_id] = moved_count_map[ticket_category_id] + 1
            moved_seat_map[ticket_category_id][seat_id_text] = true
            total_moved_count = total_moved_count + 1
        end
    end
end

for index, increase_data in ipairs(ticket_category_list) do
    local program_ticket_remain_number_hash_key = increase_data.programTicketRemainNumberHashKey
    local ticket_category_id = tostring(increase_data.ticketCategoryId)
    local increase_count = tonumber(increase_data.count)
    local moved_count = moved_count_map[ticket_category_id] or 0
    if moved_count > 0 then
        if increase_count < 0 then
            redis.call('HINCRBY', program_ticket_remain_number_hash_key, ticket_category_id, '-' .. moved_count)
        else
            redis.call('HINCRBY', program_ticket_remain_number_hash_key, ticket_category_id, moved_count)
        end
    end
end

for index, seat in pairs(add_seat_data_list) do
    local seat_hash_key_add = seat.seatHashKeyAdd
    local ticket_category_id = tostring(seat.ticketCategoryId)
    local seat_data_list = seat.seatDataList
    local moved_seats = moved_seat_map[ticket_category_id] or {}
    local moved_seat_data_list = {}
    for seat_index = 1, #seat_data_list, 2 do
        local seat_id = tostring(seat_data_list[seat_index])
        if moved_seats[seat_id] then
            table.insert(moved_seat_data_list, seat_id)
            table.insert(moved_seat_data_list, seat_data_list[seat_index + 1])
        end
    end
    if #moved_seat_data_list > 0 then
        redis.call('HMSET', seat_hash_key_add, unpack(moved_seat_data_list))
    end
end

return total_moved_count
