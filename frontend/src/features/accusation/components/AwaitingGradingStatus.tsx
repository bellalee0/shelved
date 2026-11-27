import type { AccusationResponse, RoomStatus } from '../../../shared/api/types';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import type { AccusationController } from '../hooks/useAccusation';
import type { AccusationSuspect } from './AccusationRecordList';
import { GradingRetry } from './GradingRetry';

type AwaitingGradingStatusProps = {
  /** 게임 화면이 가진 지목 상태 (처리 중 확인·재채점) */
  accusation: AccusationController;
  /** GET state의 채점 중·채점 실패 지목. 표시 내용의 기준이다 */
  pendingAccusation: AccusationResponse | null;
  suspects: AccusationSuspect[];
  timerStatus: RoomStatus | null;
  serverClockOffsetMs: number;
  /** "나중에 다시 시도": 세션을 서버에 남겨 두고 나간다 */
  onLater: () => void;
};

/**
 * 시간 종료·채점 대기 화면의 지목 상태: 제출한 답안과 채점 중 안내 또는 재채점(GradingRetry).
 * 새 답안 입력은 없다. 확인·재채점은 useAccusation이 하며 여기서는 그 상태를 그리기만 한다.
 */
export function AwaitingGradingStatus({
  accusation,
  pendingAccusation,
  suspects,
  timerStatus,
  serverClockOffsetMs,
  onLater,
}: AwaitingGradingStatusProps) {
  const { state, isBusy } = accusation;
  const input = state.phase === 'idle' ? null : state.input;
  const trickExplanation = input?.trickExplanation ?? pendingAccusation?.trickExplanation ?? null;
  const suspectName =
    pendingAccusation?.suspectName ??
    suspects.find((suspect) => suspect.suspectId === input?.suspectId)?.name ??
    '인물';
  const failed = state.phase === 'failed';
  const grading =
    isBusy || (state.phase === 'idle' && pendingAccusation?.gradingStatus === 'PENDING');

  return (
    <div className="flex flex-col gap-3">
      {trickExplanation !== null && (
        <div className="flex flex-col gap-2 rounded-md border border-line px-3 py-3 text-sm">
          <p className="flex items-center justify-between gap-3">
            <span className="font-bold">제출한 답안 · {suspectName}</span>
            {(grading || failed) && (
              <span className="rounded-full border border-line px-2 py-0.5 text-xs">
                {failed ? '채점 실패' : '채점 중'}
              </span>
            )}
          </p>
          <p className="max-h-32 overflow-y-auto break-words whitespace-pre-wrap text-muted">
            {trickExplanation}
          </p>
        </div>
      )}
      {grading && <LoadingIndicator label="채점 중입니다…" />}
      {failed && (
        <GradingRetry
          retryAvailableAt={state.error?.retryAvailableAt ?? null}
          serverClockOffsetMs={serverClockOffsetMs}
          timerStatus={timerStatus}
          onRetry={() => void accusation.retry()}
          onLater={onLater}
        />
      )}
      {state.phase === 'idle' && state.error && (
        <p role="alert" className="text-sm text-danger">
          {state.error.message}
        </p>
      )}
    </div>
  );
}
