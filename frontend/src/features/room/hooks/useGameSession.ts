import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { isApiError } from '../../../shared/api/apiError';
import { setConnectionId } from '../../../shared/api/client';
import { EventBus } from '../../../game/EventBus';
import { GameEvents } from '../../../game/events';
import { getBriefing, getRoomState, pauseRoom, resumeRoom, startRoom } from '../api';
import { decideSessionPhase, type SessionPhase } from '../sessionPhase';
import { createTimerSnapshot, type TimerSnapshot } from '../timer';
import type {
  RoomBriefingResponse,
  RoomConnectionResponse,
  RoomMapResponse,
  RoomStateResponse,
  RoomTimerResponse,
} from '../types';
import { roomMapQueryOptions } from './useRoomMap';
import { useDisplayRemainingMs } from './useDisplayRemainingMs';
import { useHeartbeat } from './useHeartbeat';

type Connection = {
  connectionId: string;
  heartbeatIntervalMs: number;
};

type SessionData = {
  phase: SessionPhase;
  /** 입장 실패 시 보여 줄 백엔드 message */
  enterError: string | null;
  roomState: RoomStateResponse | null;
  briefing: RoomBriefingResponse | null;
  mapData: RoomMapResponse | null;
  timer: TimerSnapshot | null;
  connection: Connection | null;
};

const INITIAL_SESSION: SessionData = {
  phase: 'entering',
  enterError: null,
  roomState: null,
  briefing: null,
  mapData: null,
  timer: null,
  connection: null,
};

/**
 * 게임 화면의 세션 흐름을 한 곳에서 관리한다 (docs/api/room.md "입장·재접속 흐름").
 *
 * 들어오면 항상 재접속 흐름으로 시작한다: pause(헤더 없음) → state·briefing·map 병렬 → 단계 분기.
 * 개인 노트는 useNote가, 힌트·지목 기록은 3~4주차 작업이 따로 불러온다.
 * - start·resume은 사용자 버튼으로만 보낸다 (StrictMode에서 effect가 두 번 돌아도 한 번만 나가게)
 * - 페이지를 떠날 때 pause를 보내지 않는다. 하트비트를 멈추고 connectionId만 지운다 (탭 닫기는 서버 이탈 감지가 처리)
 * - connectionId는 메모리(setConnectionId)에만 둔다
 * - 연결을 잃었다가 다시 연결하면(재입장, 하트비트 PAUSED 뒤 이어하기) reconnectKey를 올려 노트를 다시 받게 한다
 */
export function useGameSession(roomId: number, userId: number) {
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const [session, setSession] = useState<SessionData>(INITIAL_SESSION);
  const [actionError, setActionError] = useState<string | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);
  // 입장·상태 재조회의 회차. 늦게 도착한 이전 회차 응답(StrictMode의 첫 effect 포함)은 버린다
  const runRef = useRef(0);
  // start·resume·재조회 중복 실행 방지. 같은 틱의 두 번 클릭도 막도록 ref로 본다
  const submittingRef = useRef(false);
  // 이 화면이 연결을 잃었다가 다시 연결할 때마다 1씩 올린다. 노트가 이 값으로 서버 내용을 다시 받는다
  const [reconnectKey, setReconnectKey] = useState(0);
  // 연결이 있다가 사라졌는지 (하트비트 PAUSED·연결 교체 등). 다음 start·resume 성공이 재연결이다
  const lostConnectionRef = useRef(false);

  // 현재 연결. 응답 처리 중 최신 값을 동기적으로 보려고 상태와 함께 ref에도 둔다
  const connectionRef = useRef<Connection | null>(null);

  const updateConnection = useCallback((connection: Connection | null) => {
    if (connectionRef.current !== null && connection === null) {
      lostConnectionRef.current = true;
    }
    connectionRef.current = connection;
    setConnectionId(connection?.connectionId ?? null);
  }, []);

  /** 서버 타이머로 단계를 정하고 화면 타이머를 다시 맞춘다. 진행 중이 아니게 되면 연결을 지운다 */
  const applyTimer = useCallback(
    (
      timer: RoomTimerResponse,
      options: { connection?: Connection | null; roomState?: RoomStateResponse } = {},
    ) => {
      const connection =
        options.connection !== undefined ? options.connection : connectionRef.current;
      const phase = decideSessionPhase(timer, connection !== null);
      // PAUSED 전환·종료·채점 대기·다른 화면 진행이면 하트비트를 멈추도록 연결을 지운다
      const nextConnection = phase === 'playing' ? connection : null;
      updateConnection(nextConnection);
      setSession((prev) => {
        const roomState = options.roomState ?? prev.roomState;
        return {
          ...prev,
          phase,
          enterError: null,
          roomState: roomState ? { ...roomState, timer } : null,
          timer: createTimerSnapshot(timer),
          connection: nextConnection,
        };
      });
    },
    [updateConnection],
  );

  const runEntry = useCallback(
    async (run: number) => {
      try {
        await pauseRoom(roomId);
        const [roomState, briefing, mapData] = await Promise.all([
          getRoomState(roomId),
          getBriefing(roomId),
          queryClient.fetchQuery(roomMapQueryOptions(roomId, userId)),
        ]);
        // 힌트(GET hints)·지목(GET accusations) 복원은 3~4주차 API가 생기면 여기에 함께 불러온다
        if (run !== runRef.current) {
          return;
        }
        setSession((prev) => ({ ...prev, briefing, mapData }));
        applyTimer(roomState.timer, { connection: null, roomState });
      } catch (error) {
        if (run !== runRef.current) {
          return;
        }
        setSession((prev) => ({
          ...prev,
          phase: 'enterFailed',
          enterError: error instanceof Error ? error.message : String(error),
        }));
      }
    },
    [roomId, userId, queryClient, applyTimer],
  );

  useEffect(() => {
    const run = ++runRef.current;
    void runEntry(run);
    return () => {
      runRef.current += 1;
      updateConnection(null);
    };
  }, [runEntry, updateConnection]);

  /** 재접속 흐름을 처음부터 다시 한다 ("이 화면에서 계속하기", 입장 실패 후 다시 시도) */
  const reenter = useCallback(() => {
    updateConnection(null);
    setActionError(null);
    // 다시 들어오는 동안 다른 화면이 저장했을 수 있는 노트를 함께 다시 받는다 (room.md 재접속 흐름의 GET notes/me)
    lostConnectionRef.current = false;
    setReconnectKey((key) => key + 1);
    setSession((prev) => ({ ...prev, phase: 'entering', enterError: null, connection: null }));
    void runEntry(++runRef.current);
  }, [runEntry, updateConnection]);

  /** state만 다시 조회해 분기한다 (start·resume 409, 화면 타이머 0, 조작 API의 세션 상태 오류) */
  const checkState = useCallback(async () => {
    const run = ++runRef.current;
    try {
      const roomState = await getRoomState(roomId);
      if (run === runRef.current) {
        applyTimer(roomState.timer, { roomState });
      }
    } catch (error) {
      // 진행 중이면 다음 하트비트가 다시 맞춘다. 버튼 화면에서는 오류를 보여 준다
      if (run === runRef.current) {
        setActionError(error instanceof Error ? error.message : String(error));
      }
    }
  }, [roomId, applyTimer]);

  const connect = useCallback(
    async (request: (roomId: number) => Promise<RoomConnectionResponse>) => {
      if (submittingRef.current) {
        return;
      }
      submittingRef.current = true;
      setIsSubmitting(true);
      setActionError(null);
      try {
        const response = await request(roomId);
        const reconnected = lostConnectionRef.current;
        applyTimer(response.timer, {
          connection: {
            connectionId: response.connectionId,
            heartbeatIntervalMs: response.heartbeatIntervalMs,
          },
        });
        if (reconnected && connectionRef.current !== null) {
          // 연결이 끊긴 사이 다른 화면이 노트를 저장했을 수 있다
          lostConnectionRef.current = false;
          setReconnectKey((key) => key + 1);
        }
        // 재접속은 항상 시작점에서 한다. 버튼은 MAP_READY 뒤에만 눌리므로 Scene이 구독 중이다
        EventBus.emit(GameEvents.PLACE_AT_SPAWN);
      } catch (error) {
        if (isApiError(error) && error.status === 409) {
          // 로딩 중 상태가 바뀌었을 수 있다 (다른 탭에서 시작·이어하기, 시간 만료 등)
          await checkState();
        } else {
          setActionError(error instanceof Error ? error.message : String(error));
        }
      } finally {
        submittingRef.current = false;
        setIsSubmitting(false);
      }
    },
    [roomId, applyTimer, checkState],
  );

  const start = useCallback(() => connect(startRoom), [connect]);
  const resume = useCallback(() => connect(resumeRoom), [connect]);

  const markReplaced = useCallback(() => {
    updateConnection(null);
    setSession((prev) => ({ ...prev, phase: 'replaced', connection: null }));
  }, [updateConnection]);

  /** 조작 API가 세션 상태 때문에 거부됐을 때 (물증 조사·노트 저장의 409 등) */
  const handleSessionRejected = useCallback(
    (code: string) => {
      if (code === 'CONNECTION_REPLACED') {
        markReplaced();
      } else {
        void checkState();
      }
    },
    [markReplaced, checkState],
  );

  /** 저장 후 나가기에서 pause가 끝난 뒤 부른다. 하트비트를 멈추고 연결을 지운다 */
  const disconnect = useCallback(() => {
    updateConnection(null);
    setSession((prev) => ({ ...prev, connection: null }));
  }, [updateConnection]);

  const heartbeatState = useHeartbeat({
    roomId,
    connectionId: session.connection?.connectionId ?? null,
    intervalMs: session.connection?.heartbeatIntervalMs ?? 0,
    onTimer: applyTimer,
    onConnectionReplaced: markReplaced,
  });

  const displayRemainingMs = useDisplayRemainingMs(session.timer);

  // 화면 타이머가 0이 되면 만료를 확정하지 않고 서버 상태를 한 번 확인한다 (기준점마다 한 번).
  // 서버가 처음부터 0을 준 경우는 다시 묻지 않고 다음 하트비트를 기다린다 (반복 조회 방지)
  const checkedSnapshotRef = useRef<TimerSnapshot | null>(null);
  useEffect(() => {
    const snapshot = session.timer;
    if (
      session.phase === 'playing' &&
      snapshot !== null &&
      snapshot.timer.remainingMs > 0 &&
      displayRemainingMs === 0 &&
      checkedSnapshotRef.current !== snapshot
    ) {
      checkedSnapshotRef.current = snapshot;
      void checkState();
    }
  }, [session.phase, session.timer, displayRemainingMs, checkState]);

  useEffect(() => {
    if (session.phase === 'ended') {
      navigate(`/rooms/${roomId}/result`, { replace: true });
    }
  }, [session.phase, roomId, navigate]);

  return {
    phase: session.phase,
    enterError: session.enterError,
    roomState: session.roomState,
    briefing: session.briefing,
    mapData: session.mapData,
    hasConnection: session.connection !== null,
    /** 연결을 잃었다가 다시 연결할 때마다 바뀐다. useNote의 resyncKey로 넘긴다 */
    reconnectKey,
    displayRemainingMs,
    isConnectionUnstable: heartbeatState.isUnstable,
    /** start·resume 실패 문구 (409는 재조회로 처리하므로 여기에 오지 않는다) */
    actionError,
    isSubmitting,
    start,
    resume,
    reenter,
    checkState,
    handleSessionRejected,
    disconnect,
  };
}
