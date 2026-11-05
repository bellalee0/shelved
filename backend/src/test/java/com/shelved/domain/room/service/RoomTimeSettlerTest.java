package com.shelved.domain.room.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.room.dto.RoomTimerResponse;
import com.shelved.domain.room.repository.PlayResultRepository;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomTimeSettlerTest {

    private static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 5, 14, 0);
    private static final int LIMIT_SEC = 1200;
    private static final long LIMIT_MS = LIMIT_SEC * 1000L;

    @Mock
    private AccusationRepository accusationRepository;

    @Mock
    private HintRepository hintRepository;

    @Mock
    private PlayResultRepository playResultRepository;

    private RoomTimeSettler settler;

    @BeforeEach
    void setUp() {
        GameProperties gameProperties = new GameProperties(
                new GameProperties.TimeLimit(Duration.ofMinutes(20), Duration.ofMinutes(25), Duration.ofMinutes(35)),
                new GameProperties.HintLimit(5, 3, 1),
                3,
                Duration.ofSeconds(5),
                Duration.ofSeconds(15),
                Duration.ofSeconds(10),
                Duration.ofSeconds(5));
        settler = new RoomTimeSettler(accusationRepository, hintRepository, playResultRepository, gameProperties);
    }

    @Test
    void settle_이탈기준이상_lastSeen까지만누적하고PAUSED() {

        // given: 100초에 마지막 접속 확인, 130초에 정산
        Room room = startedRoom();
        room.touch(T0.plusSeconds(100));

        // when
        settler.settle(room, T0.plusSeconds(130));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(100_000L);
        assertThat(room.getActiveSince()).isNull();
        assertThat(room.getConnectionId()).isNull();
        verify(playResultRepository, never()).save(any());
    }

    @Test
    void settle_이탈기준정확히15초_이탈확정() {

        // given
        Room room = startedRoom();
        room.touch(T0.plusSeconds(10));

        // when
        settler.settle(room, T0.plusSeconds(25));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(10_000L);
    }

    @Test
    void settle_이탈시점에이미소진_EXPIRED와PlayResult생성() {

        // given: 제한시간 직후(1201초)에 마지막 접속 확인, 그 뒤 끊김
        Room room = startedRoom();
        room.touch(T0.plusSeconds(LIMIT_SEC + 1));
        given(accusationRepository.existsByRoomIdAndGradingStatusIn(any(), anyCollection())).willReturn(false);
        given(hintRepository.countByRoomId(any())).willReturn(2L);

        // when
        LocalDateTime now = T0.plusSeconds(LIMIT_SEC + 30);
        settler.settle(room, now);

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(LIMIT_MS);
        assertThat(room.getConnectionId()).isNull();
        ArgumentCaptor<PlayResult> captor = ArgumentCaptor.forClass(PlayResult.class);
        verify(playResultRepository).save(captor.capture());
        PlayResult playResult = captor.getValue();
        assertThat(playResult.isCleared()).isFalse();
        assertThat(playResult.getDurationSec()).isEqualTo(LIMIT_SEC);
        assertThat(playResult.getHintUsedCount()).isEqualTo(2);
        assertThat(playResult.getEndedAt()).isEqualTo(now);
    }

    @Test
    void settle_이탈전에남은시간있었음_마감이지나도PAUSED() {

        // given: 1190초에 마지막 접속 확인, 1250초에 정산 → 끊긴 시점 기준으로 10초 남음
        Room room = startedRoom();
        room.touch(T0.plusSeconds(LIMIT_SEC - 10));

        // when
        settler.settle(room, T0.plusSeconds(LIMIT_SEC + 50));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(LIMIT_MS - 10_000L);
    }

    @Test
    void settle_유예구간에서소진_만료보류하고상태불변() {

        // given: 1195초에 접속 확인, 1205초에 조회 (10초 미수신 < 15초)
        Room room = startedRoom();
        room.touch(T0.plusSeconds(LIMIT_SEC - 5));
        LocalDateTime now = T0.plusSeconds(LIMIT_SEC + 5);

        // when
        settler.settle(room, now);
        RoomTimerResponse timer = settler.timerOf(room, now);

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(room.getActiveSince()).isEqualTo(T0);
        assertThat(timer.remainingMs()).isZero();
        verify(playResultRepository, never()).save(any());
    }

    @Test
    void confirmPresence_소진_만료() {

        // given: 유예 구간에서 하트비트(접속 확인)
        Room room = startedRoom();
        room.touch(T0.plusSeconds(LIMIT_SEC - 5));
        LocalDateTime now = T0.plusSeconds(LIMIT_SEC + 5);
        given(accusationRepository.existsByRoomIdAndGradingStatusIn(any(), anyCollection())).willReturn(false);

        // when
        settler.settle(room, now);
        settler.confirmPresence(room, now);

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(room.getLastSeenAt()).isEqualTo(now);
        verify(playResultRepository).save(any());
    }

    @Test
    void confirmPresence_남은시간있음_lastSeen만갱신() {

        // given
        Room room = startedRoom();
        LocalDateTime now = T0.plusSeconds(30);

        // when
        settler.confirmPresence(room, now);

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.IN_PROGRESS);
        assertThat(room.getLastSeenAt()).isEqualTo(now);
        assertThat(room.getElapsedActiveMs()).isZero();
    }

    @Test
    void pause_이탈기준미만_now까지누적하고lastSeen갱신안함() {

        // given: 100초 접속 확인, 110초 pause
        Room room = startedRoom();
        room.touch(T0.plusSeconds(100));

        // when
        settler.pause(room, T0.plusSeconds(110));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(110_000L);
        assertThat(room.getLastSeenAt()).isEqualTo(T0.plusSeconds(100));
        assertThat(room.getConnectionId()).isNull();
    }

    @Test
    void pause_이탈기준이상_lastSeen까지만누적() {

        // given: 100초 접속 확인, 140초 pause
        Room room = startedRoom();
        room.touch(T0.plusSeconds(100));

        // when
        settler.pause(room, T0.plusSeconds(140));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(100_000L);
        assertThat(room.getLastSeenAt()).isEqualTo(T0.plusSeconds(100));
    }

    @Test
    void pause_유예구간에서소진_EXPIRED() {

        // given
        Room room = startedRoom();
        room.touch(T0.plusSeconds(LIMIT_SEC - 5));
        given(accusationRepository.existsByRoomIdAndGradingStatusIn(any(), anyCollection())).willReturn(false);

        // when
        settler.pause(room, T0.plusSeconds(LIMIT_SEC + 5));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(LIMIT_MS);
        verify(playResultRepository).save(any());
    }

    @Test
    void pause_소진했지만미해결지목있음_PAUSED와awaitingGrading() {

        // given
        Room room = startedRoom();
        room.touch(T0.plusSeconds(LIMIT_SEC - 5));
        given(accusationRepository.existsByRoomIdAndGradingStatusIn(any(), anyCollection())).willReturn(true);
        LocalDateTime now = T0.plusSeconds(LIMIT_SEC + 5);

        // when
        settler.pause(room, now);
        RoomTimerResponse timer = settler.timerOf(room, now);

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(LIMIT_MS);
        assertThat(room.getConnectionId()).isNull();
        assertThat(timer.awaitingGrading()).isTrue();
        assertThat(timer.remainingMs()).isZero();
        verify(playResultRepository, never()).save(any());
    }

    @Test
    void settleAndPause_PAUSED중_시간이흘러도불변() {

        // given: 100초에 일시정지한 세션을 1시간 뒤 정산
        Room room = startedRoom();
        room.pause(100_000L);
        LocalDateTime later = T0.plusHours(1);

        // when
        settler.settle(room, later);
        settler.pause(room, later);
        RoomTimerResponse timer = settler.timerOf(room, later);

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.PAUSED);
        assertThat(room.getElapsedActiveMs()).isEqualTo(100_000L);
        assertThat(timer.remainingMs()).isEqualTo(LIMIT_MS - 100_000L);
        assertThat(timer.awaitingGrading()).isFalse();
    }

    @Test
    void expire_PlayResult가이미있음_다시만들지않음() {

        // given
        Room room = startedRoom();
        given(accusationRepository.existsByRoomIdAndGradingStatusIn(any(), anyCollection())).willReturn(false);
        given(playResultRepository.existsByRoomId(any())).willReturn(true);

        // when
        settler.expire(room, T0.plusSeconds(LIMIT_SEC));

        // then
        assertThat(room.getStatus()).isEqualTo(RoomStatus.EXPIRED);
        verify(playResultRepository, never()).save(any());
    }

    private static Room startedRoom() {
        Room room = new Room(null, null, false, LIMIT_SEC, T0.minusMinutes(1));
        room.start(T0, "connection");
        return room;
    }
}
