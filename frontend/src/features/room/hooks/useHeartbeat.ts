import { useEffect, useRef, useState } from 'react';
import { isApiError } from '../../../shared/api/apiError';
import { heartbeat } from '../api';
import type { RoomTimerResponse } from '../types';

/** 연속으로 이만큼 실패하면 HUD에 "연결이 불안정합니다"를 표시한다 */
export const HEARTBEAT_UNSTABLE_FAILURE_COUNT = 2;

type UseHeartbeatParams = {
  roomId: number;
  /** null이면 하트비트를 보내지 않는다. 값이 바뀌면 처음부터 다시 센다 */
  connectionId: string | null;
  intervalMs: number;
  /** 성공 응답의 서버 타이머 */
  onTimer: (timer: RoomTimerResponse) => void;
  /** 409 CONNECTION_REPLACED. 하트비트는 이 콜백 뒤로 멈춘다 */
  onConnectionReplaced: () => void;
};

/**
 * 하트비트를 보내는 유일한 곳 (frontend.md "게임 상태·시간 처리 규칙").
 * 연결이 있는 동안 intervalMs마다 보낸다. 앞 요청이 끝난 뒤에 다음 주기를 예약하므로 한 번에 하나만 나간다.
 * 창·입력·백그라운드 탭과 무관하게 계속 보낸다. 네트워크 오류는 다음 주기에 다시 보낸다.
 */
export function useHeartbeat({
  roomId,
  connectionId,
  intervalMs,
  onTimer,
  onConnectionReplaced,
}: UseHeartbeatParams) {
  const [failureCount, setFailureCount] = useState(0);
  // 콜백이 바뀌어도 주기를 다시 시작하지 않도록 최신 콜백을 ref로 본다
  const callbacksRef = useRef({ onTimer, onConnectionReplaced });
  useEffect(() => {
    callbacksRef.current = { onTimer, onConnectionReplaced };
  });

  useEffect(() => {
    if (connectionId === null) {
      return;
    }
    let stopped = false;
    let timeoutId: ReturnType<typeof setTimeout> | undefined;

    const schedule = () => {
      timeoutId = setTimeout(send, intervalMs);
    };

    async function send() {
      try {
        const timer = await heartbeat(roomId);
        if (stopped) {
          return;
        }
        setFailureCount(0);
        callbacksRef.current.onTimer(timer);
      } catch (error) {
        if (stopped) {
          return;
        }
        if (isApiError(error) && error.code === 'CONNECTION_REPLACED') {
          stopped = true;
          callbacksRef.current.onConnectionReplaced();
          return;
        }
        setFailureCount((count) => count + 1);
      }
      // 콜백이 연결을 지웠으면 이 effect가 정리되며 stopped가 된다
      if (!stopped) {
        schedule();
      }
    }

    schedule();
    return () => {
      stopped = true;
      clearTimeout(timeoutId);
      setFailureCount(0);
    };
  }, [roomId, connectionId, intervalMs]);

  return { isUnstable: failureCount >= HEARTBEAT_UNSTABLE_FAILURE_COUNT };
}
