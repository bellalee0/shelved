import { useEffect, useState } from 'react';
import type { RoomStatus } from '../../../shared/api/types';
import { Button } from '../../../shared/components/Button';
import { gradingRetryBlockReason, secondsUntilRetry } from '../accusations';

export type GradingRetryProps = {
  /** 재채점 가능 서버 시각 (채점 실패 응답·상태 조회·429에서 받는다). 모르면 null */
  retryAvailableAt: string | null;
  /** 서버 시각 − 브라우저 시각(ms). 게임 화면 타이머가 받은 serverTime으로 계산한다 */
  serverClockOffsetMs: number;
  /** GET state의 timer.status. 종료된 세션이면 재채점할 수 없다 */
  timerStatus: RoomStatus | null;
  /** 재채점 요청을 보내는 중 */
  sending?: boolean;
  onRetry: () => void;
  /** "나중에 다시 시도": 세션을 서버에 남겨 두고 메인으로 나간다 */
  onLater: () => void;
};

export const GRADING_FAILED_MESSAGE = '채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.';

/** 화면을 다시 그릴 간격. 남은 초를 줄여 보여 준다 */
const TICK_INTERVAL_MS = 1000;

/**
 * 채점 실패 안내와 "같은 답안 다시 채점"·"나중에 다시 시도" 버튼.
 * 지목 창과 시간 종료·채점 대기 화면(SessionOverlay, FE-12)이 함께 쓴다.
 * - 최소 간격(retryAvailableAt) 전에는 버튼을 비활성화하고 남은 초를 보여 준다
 * - 자동으로 다시 보내지 않는다. 사용자가 누를 때만 onRetry를 부른다
 */
export function GradingRetry({
  retryAvailableAt,
  serverClockOffsetMs,
  timerStatus,
  sending = false,
  onRetry,
  onLater,
}: GradingRetryProps) {
  const [nowMs, setNowMs] = useState(() => Date.now());
  const serverNowMs = nowMs + serverClockOffsetMs;
  const waitSeconds = secondsUntilRetry(retryAvailableAt, serverNowMs);
  const waiting = waitSeconds > 0;

  // 기다리는 동안만 1초마다 다시 그린다
  useEffect(() => {
    if (!waiting) {
      return;
    }
    const id = setInterval(() => setNowMs(Date.now()), TICK_INTERVAL_MS);
    return () => clearInterval(id);
  }, [waiting]);

  const reason = gradingRetryBlockReason({
    failed: true,
    superseded: false,
    timerStatus,
    retryAvailableAt,
    serverNowMs,
  });

  return (
    <div
      role="alert"
      className="flex flex-col gap-2 rounded-md bg-danger-soft px-4 py-3 text-sm text-danger"
    >
      <p>{GRADING_FAILED_MESSAGE}</p>
      {reason === 'TOO_SOON' && (
        <p className="text-xs" aria-live="polite">
          {waitSeconds}초 후 다시 채점할 수 있습니다.
        </p>
      )}
      <div className="flex flex-wrap gap-2">
        <Button
          variant="secondary"
          disabled={reason !== null}
          isLoading={sending}
          onClick={onRetry}
        >
          같은 답안 다시 채점
        </Button>
        <Button variant="ghost" onClick={onLater} disabled={sending}>
          나중에 다시 시도
        </Button>
      </div>
    </div>
  );
}
