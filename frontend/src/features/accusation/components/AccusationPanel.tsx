import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { AccusationResponse, RoomStatus } from '../../../shared/api/types';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { Modal } from '../../../shared/components/Modal';
import { SidePanel } from '../../../shared/components/SidePanel';
import { formatUsage } from '../../../shared/utils/formatUsage';
import type { UsageCount } from '../../room/types';
import {
  accusationSubmitBlockReason,
  isEndedRoomStatus,
  isPastDeadline,
  withGradingReservation,
} from '../accusations';
import type { AccusationController } from '../hooks/useAccusation';
import { useAccusations } from '../hooks/useAccusations';
import type { AccusationCreateResponse } from '../types';
import { AccusationForm } from './AccusationForm';
import {
  AccusationRecordItem,
  RecordStatus,
  VerdictFeedback,
  type AccusationSuspect,
} from './AccusationRecordList';
import { GradingRetry } from './GradingRetry';

type AccusationPanelProps = {
  roomId: number;
  /** 게임 화면이 가진 지목 상태 (useAccusation). 창을 닫아도 초안·채점 중 확인이 남는다 */
  accusation: AccusationController;
  /** 지목 대상 전체 (GET map의 suspects, 맵에 배치되지 않은 인물 포함) */
  suspects: AccusationSuspect[];
  /** 인물 얼굴. 스프라이트가 없으면 기본 실루엣을 그린다 (사전 정보 인물 목록과 같은 방식) */
  renderFace?: (suspect: AccusationSuspect) => ReactNode;
  /** GET state의 timer.status */
  timerStatus: RoomStatus | null;
  /** 화면 타이머의 남은 시간 */
  remainingMs: number | null;
  awaitingGrading: boolean;
  /** 현재 조작 연결이 있는지 (연결 교체면 false) */
  hasConnection: boolean;
  /** GET state의 현재 지목 횟수 */
  accusationCount: UsageCount | null;
  /** GET state의 채점 중·채점 실패 지목. 표시 내용의 기준이다 */
  pendingAccusation: AccusationResponse | null;
  /** GET state로 세션 종료를 확인했다 (게임 화면 단계가 ended) */
  sessionEnded: boolean;
  /** 서버 시각 − 브라우저 시각(ms). 재채점 대기 초 계산에 쓴다 */
  serverClockOffsetMs: number;
  /** 재접속할 때마다 바뀌는 값. 바뀌면 기록을 다시 받는다 */
  resyncKey: number;
  /** "결과 보기" */
  onShowResult: () => void;
  /** "나중에 다시 시도": 저장 후 나가기와 같은 흐름으로 메인에 간다 */
  onRetryLater: () => void;
  onClose: () => void;
};

const ENDED_MESSAGES: Record<'CLEARED' | 'FAILED' | 'EXPIRED', string> = {
  CLEARED: '정답입니다! 사건을 해결했습니다.',
  FAILED: '지목 기회를 모두 사용했습니다. 수사가 끝났습니다.',
  EXPIRED: '제한시간이 끝나 수사가 끝났습니다.',
};

/**
 * 범인 지목 창. 열 때 지목 기록·횟수를 불러오고, 범인 한 명과 트릭 설명을 골라 제출한다.
 * 창을 닫아도 채점 요청은 취소되지 않고, 입력 초안과 채점 중 상태는 게임 화면(useAccusation)이 들고 있다.
 *
 * 한 지목의 흐름: 입력 → "제출" → 확인 → 채점 중(잠금) → 결과(판정·피드백) 또는 채점 실패(같은 답안 다시 채점).
 * 마감 후(남은 시간 0·채점 대기)에는 새 답안 입력을 막고 재채점만 보여 준다.
 */
export function AccusationPanel({
  roomId,
  accusation,
  suspects,
  renderFace,
  timerStatus,
  remainingMs,
  awaitingGrading,
  hasConnection,
  accusationCount,
  pendingAccusation,
  sessionEnded,
  serverClockOffsetMs,
  resyncKey,
  onShowResult,
  onRetryLater,
  onClose,
}: AccusationPanelProps) {
  const listQuery = useAccusations(roomId);
  const { refetch } = listQuery;
  const resultRef = useRef<HTMLDivElement>(null);

  // 재접속하면 열려 있던 창의 기록을 다시 받는다 (NPC·힌트 창과 같은 방식)
  const handledResyncKeyRef = useRef(resyncKey);
  useEffect(() => {
    if (handledResyncKeyRef.current === resyncKey) {
      return;
    }
    handledResyncKeyRef.current = resyncKey;
    void refetch();
  }, [resyncKey, refetch]);

  const { state, isBusy, latestResult } = accusation;

  // 제출·재채점을 누르면 채점 중 표시가 창 위쪽에 있어 보이지 않으므로 그 위치로 옮긴다.
  // 이미 채점 중인 창을 열 때(처음 값이 true)는 옮기지 않는다
  const gradingRef = useRef<HTMLDivElement>(null);
  const wasBusyRef = useRef(isBusy);
  useEffect(() => {
    if (isBusy && !wasBusyRef.current) {
      gradingRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'nearest' });
    }
    wasBusyRef.current = isBusy;
  }, [isBusy]);
  const activeRequestId = state.phase === 'idle' ? null : state.requestId;
  const records = listQuery.data?.accusations ?? [];

  // 방금 받은 결과가 생기면 그 위치를 보여 준다 (jsdom에는 scrollIntoView가 없다)
  useEffect(() => {
    if (latestResult) {
      resultRef.current?.scrollIntoView?.({ block: 'nearest' });
    }
  }, [latestResult]);

  const baseCount = accusationCount ?? listQuery.data?.accusationCount ?? null;
  const count = baseCount ? withGradingReservation(baseCount, isBusy) : null;
  const gradingInProgress = isBusy || pendingAccusation?.gradingStatus === 'PENDING';
  const pastDeadline = isPastDeadline(remainingMs, awaitingGrading);
  const blockReason = accusationSubmitBlockReason({
    timerStatus,
    remainingMs,
    awaitingGrading,
    hasConnection,
    remaining: count?.remaining ?? null,
    gradingInProgress,
    suspectId: accusation.draft.suspectId,
    trickExplanation: accusation.draft.trickExplanation,
    sameAsFailedInput: accusation.isSameAsFailedInput,
  });

  function suspectNameOf(suspectId: number) {
    return suspects.find((suspect) => suspect.suspectId === suspectId)?.name ?? '인물';
  }

  /** 이 화면이 다루는 지목(채점 중·실패)의 상태. 실패면 재채점 영역을 붙인다 */
  function renderActiveStatus() {
    if (isBusy) {
      return (
        <div ref={gradingRef}>
          <LoadingIndicator label="채점 중입니다…" />
        </div>
      );
    }
    return (
      <GradingRetry
        retryAvailableAt={state.error?.retryAvailableAt ?? null}
        serverClockOffsetMs={serverClockOffsetMs}
        timerStatus={timerStatus}
        onRetry={() => void accusation.retry()}
        onLater={onRetryLater}
      />
    );
  }

  const activeInList =
    activeRequestId !== null && records.some((record) => record.requestId === activeRequestId);
  // 기록에 아직 없는 채점 중·실패 지목 (제출 직후 등). 표시 내용은 pendingAccusation이 같은 요청이면 그 값을 쓴다
  const activeInput = state.phase === 'idle' ? null : state.input;
  const showActiveSeparately = activeRequestId !== null && !activeInList && activeInput !== null;
  const activeDisplay = pendingAccusation?.requestId === activeRequestId ? pendingAccusation : null;

  return (
    <SidePanel title="범인 지목" onClose={onClose}>
      {count !== null && (
        <p className="text-sm" aria-label="지목 횟수">
          {formatUsage('지목', count)}
        </p>
      )}

      {listQuery.isPending && <LoadingIndicator label="지목 기록을 불러오는 중…" />}
      {listQuery.isError && (
        <ErrorMessage message={listQuery.error.message} onRetry={() => void refetch()} />
      )}

      {(records.length > 0 || showActiveSeparately) && (
        <section className="flex flex-col gap-2">
          <h3 className="text-sm font-bold">이전 지목</h3>
          <ol aria-label="지목 기록" className="flex flex-col gap-3">
            {records.map((record) => {
              const isActive =
                record.requestId === activeRequestId && record.gradingStatus !== 'COMPLETED';
              const highlighted = latestResult?.accusation.accusationId === record.accusationId;
              return (
                <AccusationRecordItem
                  key={record.accusationId}
                  suspectName={record.suspectName}
                  trickExplanation={record.trickExplanation}
                  attemptNo={record.gradingStatus === 'COMPLETED' ? record.attemptNo : null}
                  highlighted={highlighted}
                  superseded={record.superseded}
                >
                  {isActive ? renderActiveStatus() : <RecordStatus accusation={record} />}
                </AccusationRecordItem>
              );
            })}
            {showActiveSeparately && activeInput && (
              <AccusationRecordItem
                suspectName={activeDisplay?.suspectName ?? suspectNameOf(activeInput.suspectId)}
                trickExplanation={activeInput.trickExplanation}
                attemptNo={null}
              >
                {renderActiveStatus()}
              </AccusationRecordItem>
            )}
          </ol>
        </section>
      )}

      {/* 기록이 아직 안 바뀌었어도 방금 받은 결과는 바로 보여 준다 */}
      {latestResult &&
        !records.some((record) => record.accusationId === latestResult.accusation.accusationId) && (
          <ol aria-label="방금 받은 결과" className="flex flex-col gap-3">
            <AccusationRecordItem
              suspectName={latestResult.accusation.suspectName}
              trickExplanation={latestResult.accusation.trickExplanation}
              attemptNo={latestResult.accusation.attemptNo}
              highlighted
            >
              <VerdictFeedback accusation={latestResult.accusation} />
            </AccusationRecordItem>
          </ol>
        )}

      <div ref={resultRef}>
        <ResultNotice
          latestResult={latestResult}
          sessionEnded={sessionEnded}
          timerStatus={timerStatus}
          remaining={count?.remaining ?? null}
          onShowResult={onShowResult}
        />
      </div>

      {state.phase === 'idle' && state.error && (
        <p role="alert" className="text-sm text-danger">
          {state.error.message}
        </p>
      )}

      {!sessionEnded && (
        <section className="mt-auto flex flex-col gap-2 border-t border-line pt-4">
          <h3 className="text-sm font-bold">새 지목</h3>
          {pastDeadline ? (
            <p className="text-sm text-muted">
              제한시간이 끝나 새 답안을 제출할 수 없습니다. 채점에 실패한 답안은 같은 답안으로 다시
              채점할 수 있습니다.
            </p>
          ) : (
            <>
              {isBusy && <LoadingIndicator label="채점 중입니다…" />}
              <AccusationForm
                suspects={suspects}
                renderFace={renderFace}
                draft={accusation.draft}
                onDraftChange={accusation.setDraft}
                locked={isBusy}
                blockReason={blockReason}
                remaining={count?.remaining ?? null}
                supersedesFailed={state.phase === 'failed'}
                onSubmit={accusation.submit}
              />
            </>
          )}
        </section>
      )}
    </SidePanel>
  );
}

type ResultNoticeProps = {
  latestResult: AccusationCreateResponse | null;
  sessionEnded: boolean;
  timerStatus: RoomStatus | null;
  /** 현재(GET state 기준) 남은 기회 */
  remaining: number | null;
  onShowResult: () => void;
};

/**
 * 방금 받은 결과 아래의 안내. 종료 여부는 결과 스냅샷(roomStatus)이 아니라 GET state로 확인한 값(sessionEnded)으로 정한다.
 * 결과를 읽을 시간을 주기 위해 결과 화면으로 자동 이동하지 않고 "결과 보기" 버튼을 둔다.
 * 지난 지목이 쌓이면 안내가 창 아래로 밀리므로, 종료되면 화면 가운데 팝업으로도 보여 준다.
 * 팝업이 지목 창을 가리기 때문에 방금 받은 판정·피드백을 함께 담고, 닫아도 창 안의 안내는 남는다.
 */
function ResultNotice({
  latestResult,
  sessionEnded,
  timerStatus,
  remaining,
  onShowResult,
}: ResultNoticeProps) {
  const [popupClosed, setPopupClosed] = useState(false);

  if (sessionEnded) {
    const endedStatus = isEndedRoomStatus(timerStatus)
      ? (timerStatus as 'CLEARED' | 'FAILED' | 'EXPIRED')
      : null;
    const message = endedStatus && (
      <p className={endedStatus === 'CLEARED' ? 'text-lg font-bold text-accent' : 'font-bold'}>
        {ENDED_MESSAGES[endedStatus]}
      </p>
    );
    const showResultButton = (
      <Button className="self-start" onClick={onShowResult}>
        결과 보기
      </Button>
    );
    return (
      <>
        <div
          role="status"
          className="flex flex-col gap-2 rounded-md border-2 border-accent bg-paper px-4 py-3"
        >
          {message}
          {showResultButton}
        </div>
        {!popupClosed && (
          <Modal title="수사 종료" onClose={() => setPopupClosed(true)}>
            <div className="flex flex-col gap-3">
              {message}
              {latestResult && (
                // 피드백이 길어도 "결과 보기"가 화면 밖으로 밀리지 않게 이 영역만 스크롤한다
                <div className="max-h-[50vh] overflow-y-auto">
                  <VerdictFeedback accusation={latestResult.accusation} />
                </div>
              )}
              {showResultButton}
            </div>
          </Modal>
        )}
      </>
    );
  }
  if (latestResult === null) {
    return null;
  }
  if (isEndedRoomStatus(latestResult.roomStatus)) {
    return <LoadingIndicator label="수사 결과를 확인하는 중…" />;
  }
  return (
    <p role="status" className="text-sm">
      {remaining !== null ? `남은 기회 ${remaining}회. ` : ''}수사를 계속하며 답안을 보완해 다시
      제출할 수 있습니다.
    </p>
  );
}
