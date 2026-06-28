package com.tikectsystem.service;

import cn.hutool.core.collection.CollectionUtil;
import com.tikectsystem.dto.ProgramOrderCircuitOperateDto;
import com.tikectsystem.dto.ProgramOrderCircuitQueryDto;
import com.tikectsystem.dto.ProgramOrderCreateDto;
import com.tikectsystem.dto.SeatDto;
import com.tikectsystem.enums.BaseCode;
import com.tikectsystem.exception.TikectsystemFrameException;
import com.tikectsystem.util.DateUtils;
import com.tikectsystem.vo.ProgramOrderCircuitStateVo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 节目下单 Redis 熔断状态机。
 */
@Slf4j
@Service
public class ProgramOrderCircuitBreakerService {

    public static final String STATE_NORMAL = "NORMAL";

    public static final String STATE_LIMITED = "LIMITED";

    public static final String STATE_FROZEN = "FROZEN";

    public static final String STATE_RECOVERING = "RECOVERING";

    public static final String STATE_HALF_OPEN = "HALF_OPEN";

    private static final int DEFAULT_LIMITED_QPS = 50;

    private static final int DEFAULT_HALF_OPEN_MAX_IN_FLIGHT = 5;

    private static final int DEFAULT_HALF_OPEN_SUCCESS_THRESHOLD = 20;

    private static final long PROGRAM_SCOPE_TICKET_CATEGORY_ID = 0L;

    private final Map<String, CircuitState> stateMap = new ConcurrentHashMap<>();

    /**
     * Redis 操作前检查票档状态，失败时直接拒绝新下单。
     */
    public CircuitAccessToken beforeRedisAccess(ProgramOrderCreateDto programOrderCreateDto) {
        Long programId = programOrderCreateDto.getProgramId();
        Long ticketCategoryId = resolveTicketCategoryId(programOrderCreateDto);
        CircuitState state = getEffectiveState(programId, ticketCategoryId);
        if (state == null || Objects.equals(state.state, STATE_NORMAL)) {
            return new CircuitAccessToken(programId, ticketCategoryId, null, false);
        }
        if (Objects.equals(state.state, STATE_LIMITED)) {
            if (allowLimitedRequest(state)) {
                return new CircuitAccessToken(programId, ticketCategoryId, state, false);
            }
            throw new TikectsystemFrameException(BaseCode.PROGRAM_ORDER_DEGRADE);
        }
        if (Objects.equals(state.state, STATE_HALF_OPEN)) {
            if (tryAcquireHalfOpen(state)) {
                return new CircuitAccessToken(programId, ticketCategoryId, state, true);
            }
            throw new TikectsystemFrameException(BaseCode.PROGRAM_ORDER_CIRCUIT_OPEN);
        }
        throw new TikectsystemFrameException(BaseCode.PROGRAM_ORDER_CIRCUIT_OPEN);
    }

    /**
     * Redis 操作成功后推进 HALF_OPEN 自动恢复。
     */
    public void afterRedisSuccess(CircuitAccessToken token) {
        if (token == null || token.state == null) {
            return;
        }
        CircuitState state = token.state;
        if (token.halfOpenPermit) {
            releaseHalfOpenPermit(state);
        }
        if (Objects.equals(state.state, STATE_HALF_OPEN)) {
            int successCount = state.halfOpenSuccessCount.incrementAndGet();
            if (successCount >= state.halfOpenSuccessThreshold) {
                synchronized (state) {
                    if (Objects.equals(state.state, STATE_HALF_OPEN)) {
                        state.state = STATE_NORMAL;
                        state.reason = "half open probe success";
                        state.updateTime = DateUtils.now();
                        state.halfOpenSuccessCount.set(0);
                        state.halfOpenInFlight.set(0);
                    }
                }
            }
        }
    }

    /**
     * Redis 操作失败后冻结对应节目票档。
     */
    public void afterRedisFailure(CircuitAccessToken token, Throwable throwable) {
        if (token != null && token.halfOpenPermit && token.state != null) {
            releaseHalfOpenPermit(token.state);
        }
        Long programId = token == null ? null : token.programId;
        Long ticketCategoryId = token == null ? null : token.ticketCategoryId;
        if (programId == null) {
            return;
        }
        String reason = throwable == null ? "redis access failed" : throwable.getClass().getSimpleName() + ":" + throwable.getMessage();
        forceState(programId, ticketCategoryId, STATE_FROZEN, reason, null, null, null);
    }

    /**
     * 手动推进熔断状态。
     */
    public ProgramOrderCircuitStateVo updateState(ProgramOrderCircuitOperateDto operateDto) {
        String state = normalizeState(operateDto.getState());
        CircuitState circuitState = forceState(operateDto.getProgramId(), operateDto.getTicketCategoryId(), state,
                operateDto.getReason(), operateDto.getLimitedQps(), operateDto.getHalfOpenMaxInFlight(),
                operateDto.getHalfOpenSuccessThreshold());
        return toVo(circuitState);
    }

    public ProgramOrderCircuitStateVo getState(ProgramOrderCircuitQueryDto queryDto) {
        CircuitState state = stateMap.get(buildKey(queryDto.getProgramId(), queryDto.getTicketCategoryId()));
        if (state == null) {
            state = new CircuitState(queryDto.getProgramId(), normalizeTicketCategoryId(queryDto.getTicketCategoryId()));
        }
        return toVo(state);
    }

    public List<ProgramOrderCircuitStateVo> listState() {
        List<ProgramOrderCircuitStateVo> result = new ArrayList<>();
        for (CircuitState state : stateMap.values()) {
            result.add(toVo(state));
        }
        return result;
    }

    private CircuitState forceState(Long programId, Long ticketCategoryId, String state, String reason,
                                    Integer limitedQps, Integer halfOpenMaxInFlight,
                                    Integer halfOpenSuccessThreshold) {
        String key = buildKey(programId, ticketCategoryId);
        CircuitState circuitState = stateMap.computeIfAbsent(key,
                ignored -> new CircuitState(programId, normalizeTicketCategoryId(ticketCategoryId)));
        synchronized (circuitState) {
            circuitState.state = state;
            circuitState.reason = reason;
            circuitState.limitedQps = limitedQps == null ? circuitState.limitedQps : limitedQps;
            circuitState.halfOpenMaxInFlight = halfOpenMaxInFlight == null ?
                    circuitState.halfOpenMaxInFlight : halfOpenMaxInFlight;
            circuitState.halfOpenSuccessThreshold = halfOpenSuccessThreshold == null ?
                    circuitState.halfOpenSuccessThreshold : halfOpenSuccessThreshold;
            circuitState.halfOpenSuccessCount.set(0);
            circuitState.halfOpenInFlight.set(0);
            circuitState.currentSecond.set(0);
            circuitState.currentSecondCount.set(0);
            circuitState.updateTime = DateUtils.now();
        }
        log.warn("program order circuit state changed, programId:{}, ticketCategoryId:{}, state:{}, reason:{}",
                programId, ticketCategoryId, state, reason);
        return circuitState;
    }

    private CircuitState getEffectiveState(Long programId, Long ticketCategoryId) {
        CircuitState ticketState = stateMap.get(buildKey(programId, ticketCategoryId));
        if (ticketState != null && !Objects.equals(ticketState.state, STATE_NORMAL)) {
            return ticketState;
        }
        CircuitState programState = stateMap.get(buildKey(programId, PROGRAM_SCOPE_TICKET_CATEGORY_ID));
        if (programState != null && !Objects.equals(programState.state, STATE_NORMAL)) {
            return programState;
        }
        return ticketState;
    }

    private boolean allowLimitedRequest(CircuitState state) {
        long nowSecond = System.currentTimeMillis() / 1000;
        long currentSecond = state.currentSecond.get();
        if (currentSecond != nowSecond && state.currentSecond.compareAndSet(currentSecond, nowSecond)) {
            state.currentSecondCount.set(0);
        }
        return state.currentSecondCount.incrementAndGet() <= state.limitedQps;
    }

    private boolean tryAcquireHalfOpen(CircuitState state) {
        while (true) {
            int current = state.halfOpenInFlight.get();
            if (current >= state.halfOpenMaxInFlight) {
                return false;
            }
            if (state.halfOpenInFlight.compareAndSet(current, current + 1)) {
                return true;
            }
        }
    }

    private void releaseHalfOpenPermit(CircuitState state) {
        while (true) {
            int current = state.halfOpenInFlight.get();
            if (current <= 0) {
                return;
            }
            if (state.halfOpenInFlight.compareAndSet(current, current - 1)) {
                return;
            }
        }
    }

    private String normalizeState(String state) {
        String normalizedState = state == null ? "" : state.trim().toUpperCase(Locale.ROOT);
        if (Objects.equals(normalizedState, STATE_NORMAL) || Objects.equals(normalizedState, STATE_LIMITED) ||
                Objects.equals(normalizedState, STATE_FROZEN) || Objects.equals(normalizedState, STATE_RECOVERING) ||
                Objects.equals(normalizedState, STATE_HALF_OPEN)) {
            return normalizedState;
        }
        throw new TikectsystemFrameException(BaseCode.PARAMETER_ERROR);
    }

    private Long resolveTicketCategoryId(ProgramOrderCreateDto programOrderCreateDto) {
        if (CollectionUtil.isNotEmpty(programOrderCreateDto.getSeatDtoList())) {
            SeatDto seatDto = programOrderCreateDto.getSeatDtoList().get(0);
            return seatDto == null ? null : seatDto.getTicketCategoryId();
        }
        return programOrderCreateDto.getTicketCategoryId();
    }

    private String buildKey(Long programId, Long ticketCategoryId) {
        return programId + ":" + normalizeTicketCategoryId(ticketCategoryId);
    }

    private Long normalizeTicketCategoryId(Long ticketCategoryId) {
        return ticketCategoryId == null ? PROGRAM_SCOPE_TICKET_CATEGORY_ID : ticketCategoryId;
    }

    private ProgramOrderCircuitStateVo toVo(CircuitState state) {
        ProgramOrderCircuitStateVo vo = new ProgramOrderCircuitStateVo();
        vo.setProgramId(state.programId);
        vo.setTicketCategoryId(Objects.equals(state.ticketCategoryId, PROGRAM_SCOPE_TICKET_CATEGORY_ID) ?
                null : state.ticketCategoryId);
        vo.setState(state.state);
        vo.setReason(state.reason);
        vo.setLimitedQps(state.limitedQps);
        vo.setHalfOpenMaxInFlight(state.halfOpenMaxInFlight);
        vo.setHalfOpenSuccessThreshold(state.halfOpenSuccessThreshold);
        vo.setHalfOpenSuccessCount(state.halfOpenSuccessCount.get());
        vo.setHalfOpenInFlight(state.halfOpenInFlight.get());
        vo.setUpdateTime(state.updateTime);
        return vo;
    }

    public static class CircuitAccessToken {

        private final Long programId;

        private final Long ticketCategoryId;

        private final CircuitState state;

        private final boolean halfOpenPermit;

        private CircuitAccessToken(Long programId, Long ticketCategoryId, CircuitState state, boolean halfOpenPermit) {
            this.programId = programId;
            this.ticketCategoryId = ticketCategoryId;
            this.state = state;
            this.halfOpenPermit = halfOpenPermit;
        }
    }

    private static class CircuitState {

        private final Long programId;

        private final Long ticketCategoryId;

        private String state = STATE_NORMAL;

        private String reason = "";

        private int limitedQps = DEFAULT_LIMITED_QPS;

        private int halfOpenMaxInFlight = DEFAULT_HALF_OPEN_MAX_IN_FLIGHT;

        private int halfOpenSuccessThreshold = DEFAULT_HALF_OPEN_SUCCESS_THRESHOLD;

        private final AtomicInteger halfOpenSuccessCount = new AtomicInteger();

        private final AtomicInteger halfOpenInFlight = new AtomicInteger();

        private final AtomicLong currentSecond = new AtomicLong();

        private final AtomicInteger currentSecondCount = new AtomicInteger();

        private Date updateTime = DateUtils.now();

        private CircuitState(Long programId, Long ticketCategoryId) {
            this.programId = programId;
            this.ticketCategoryId = ticketCategoryId;
        }
    }
}
