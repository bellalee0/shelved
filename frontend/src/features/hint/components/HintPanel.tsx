import { useEffect, useRef, useState, type ReactNode } from 'react';
import type { AiRequestState } from '../../../shared/api/aiRequestController';
import type { RoomStatus } from '../../../shared/api/types';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { SidePanel } from '../../../shared/components/SidePanel';
import { FOLLOWUP_BLOCK_MESSAGES, followupBlockReason } from '../followup';
import { formatHintUsage, withPendingReservation } from '../hints';
import { useHintChat } from '../hooks/useHintChat';
import type { FollowupQuestionInput, HintQuestionInput, HintResponse } from '../types';
import { QuestionForm } from './QuestionForm';

type HintPanelProps = {
  roomId: number;
  /** 지금 새 힌트를 보낼 수 있는 세션 상태인지 (진행 중·유효한 연결·남은 시간 > 0). 아니면 기록 읽기만 한다 */
  operable: boolean;
  /** 보충 질문 버튼 조건에 쓰는 세션 값 (GET state의 timer.status, 화면 타이머의 남은 시간, 현재 조작 연결) */
  timerStatus: RoomStatus | null;
  remainingMs: number | null;
  hasConnection: boolean;
  /** 재접속할 때마다 바뀌는 값. 바뀌면 기록을 다시 받는다 */
  resyncKey: number;
  /** 요청이 끝났거나 횟수가 바뀌었을 수 있을 때 (게임 화면이 GET state를 다시 조회한다) */
  onCountChanged?: () => void;
  /** 세션 상태 때문에 요청이 거부됐을 때의 오류 코드 */
  onSessionRejected: (code: string) => void;
  onClose: () => void;
};

const NOT_OPERABLE_MESSAGE =
  '수사가 진행 중일 때만 힌트를 요청할 수 있습니다. 이전 기록은 읽을 수 있습니다.';
const EXHAUSTED_MESSAGE = '힌트를 모두 사용했습니다. 이전 기록은 볼 수 있습니다.';
const HINT_FAILED_MESSAGE = '힌트를 받지 못했습니다. 힌트 횟수는 차감되지 않았습니다.';
const FOLLOWUP_FAILED_MESSAGE =
  '보충 질문의 답변을 받지 못했습니다. 보충 질문 기회는 그대로 남아 있습니다.';

/** 질문·답변 한 쌍. 새 힌트는 "힌트", 보충 질문은 한 단계 들여 "보충 질문"으로 구분한다 */
function QaPair({
  kind,
  question,
  answer,
  pending = false,
}: {
  kind: 'HINT' | 'FOLLOWUP';
  question: string;
  answer?: string;
  pending?: boolean;
}) {
  const isFollowup = kind === 'FOLLOWUP';
  return (
    <div
      className={`flex flex-col gap-2 ${isFollowup ? 'ml-4 border-l-2 border-accent/40 pl-3' : ''} ${
        pending ? 'opacity-70' : ''
      }`}
    >
      <p className="text-xs font-bold text-muted">
        {isFollowup ? '보충 질문 (무료)' : '새 힌트 질문'}
      </p>
      <p className="self-end rounded-md bg-accent px-3 py-2 text-sm break-words whitespace-pre-wrap text-surface">
        {question}
      </p>
      {answer !== undefined && (
        <p className="rounded-md border border-line bg-paper px-3 py-2 text-sm leading-relaxed break-words whitespace-pre-wrap text-ink">
          {answer}
        </p>
      )}
    </div>
  );
}

function FailureBlock({
  message,
  disabled,
  onRetry,
}: {
  message: string;
  disabled: boolean;
  onRetry: () => void;
}) {
  return (
    <div
      role="alert"
      className="flex flex-col gap-2 rounded-md bg-danger-soft px-4 py-3 text-sm text-danger"
    >
      <p>{message}</p>
      <Button variant="secondary" className="self-start" disabled={disabled} onClick={onRetry}>
        다시 보내기
      </Button>
    </div>
  );
}

/** 진행 중인 요청의 입력 (처리 중·실패일 때만 있다) */
function activeInput<T>(state: AiRequestState<T>): T | null {
  return state.phase === 'idle' ? null : state.input;
}

/**
 * 힌트 대화창. 열 때 힌트 기록·횟수를 불러오고(횟수를 차감하지 않는다), 새 힌트와 무료 보충 질문을 보낸다.
 * 창을 닫아도 서버의 요청은 취소되지 않는다. 다시 열면 기록의 pendingRequests로 "답변 생성 중"·"다시 보내기"를 복원한다.
 *
 * 동시 요청은 단순하게 제한한다: AI 요청이 하나라도 처리 중이면 새 힌트·보충 질문 입력을 모두 막는다.
 */
export function HintPanel({
  roomId,
  operable,
  timerStatus,
  remainingMs,
  hasConnection,
  resyncKey,
  onCountChanged,
  onSessionRejected,
  onClose,
}: HintPanelProps) {
  const [draft, setDraft] = useState('');
  // 보충 질문 입력창이 열려 있는 힌트와 그 입력 내용
  const [followupHintId, setFollowupHintId] = useState<number | null>(null);
  const [followupDraft, setFollowupDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  const { hintsQuery, hintRequest, followupRequest } = useHintChat({
    roomId,
    resyncKey,
    onCountChanged,
    // 접수되지 않은 질문(차단 등)은 입력창에 되돌린다. 그사이 새로 입력한 내용이 있으면 덮어쓰지 않는다
    onHintRejected: (input: HintQuestionInput) =>
      setDraft((current) => (current === '' ? input.question : current)),
    onFollowupRejected: (input: FollowupQuestionInput) => {
      setFollowupHintId(input.hintId);
      setFollowupDraft((current) => (current === '' ? input.question : current));
    },
    onSessionRejected,
  });

  const hints = hintsQuery.data?.hints ?? [];
  const busy = hintRequest.isBusy || followupRequest.isBusy;
  const pendingHintInput = activeInput(hintRequest.state);
  const pendingFollowupInput = activeInput(followupRequest.state);

  // 새 말풍선이 생기면 아래로 내린다 (jsdom에는 scrollIntoView가 없다)
  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [hints.length, hintRequest.state.phase, followupRequest.state.phase]);

  const count = hintsQuery.data
    ? withPendingReservation(hintsQuery.data.hintCount, hintRequest.isBusy)
    : null;
  // 처리 중으로 센 몫 때문에 0이 된 경우는 "모두 사용"이 아니다
  const exhausted = count !== null && count.remaining <= 0 && !hintRequest.isBusy;
  const hintInputDisabled = !operable || exhausted || count === null;
  const question = draft.trim();

  function handleSubmitHint() {
    if (hintRequest.submit({ question })) {
      setDraft('');
    }
  }

  function handleSubmitFollowup(hintId: number) {
    if (followupRequest.submit({ hintId, question: followupDraft.trim() })) {
      setFollowupDraft('');
      setFollowupHintId(null);
    }
  }

  function renderFollowup(hint: HintResponse): ReactNode {
    if (hint.followup) {
      return (
        <QaPair kind="FOLLOWUP" question={hint.followup.question} answer={hint.followup.content} />
      );
    }
    if (pendingFollowupInput?.hintId === hint.hintId) {
      return (
        <>
          <QaPair kind="FOLLOWUP" question={pendingFollowupInput.question} pending />
          {followupRequest.isBusy ? (
            <LoadingIndicator label="답변 생성 중…" />
          ) : (
            <FailureBlock
              message={FOLLOWUP_FAILED_MESSAGE}
              disabled={!operable}
              onRetry={() => followupRequest.retry()}
            />
          )}
        </>
      );
    }
    // 기회가 남았는지(followupAvailable)와 지금 보낼 수 있는 세션인지는 별개라서 함께 본다
    const reason = followupBlockReason({
      followupAvailable: hint.followupAvailable,
      timerStatus,
      remainingMs,
      hasConnection,
    });
    if (reason === null && followupHintId === hint.hintId) {
      return (
        <div className="ml-4 border-l-2 border-accent/40 pl-3">
          <QuestionForm
            label="보충 질문"
            placeholder="이 힌트에 대해 궁금한 점을 입력하세요 (Enter 전송, Shift+Enter 줄바꿈)"
            value={followupDraft}
            onChange={setFollowupDraft}
            onSubmit={() => handleSubmitFollowup(hint.hintId)}
            inputDisabled={false}
            busy={busy}
            submitLabel="보충 질문 보내기"
            notice="힌트 횟수는 차감되지 않습니다."
          />
        </div>
      );
    }
    const disabled = reason !== null || busy;
    return (
      <div className="ml-4 flex flex-col items-start gap-1">
        <Button
          variant="secondary"
          disabled={disabled}
          onClick={() => setFollowupHintId(hint.hintId)}
        >
          이 힌트에 대해 다시 묻기 (무료 1회)
        </Button>
        {reason !== null && <p className="text-xs text-muted">{FOLLOWUP_BLOCK_MESSAGES[reason]}</p>}
        {reason === null && busy && (
          <p className="text-xs text-muted">답변을 만드는 중에는 보낼 수 없습니다.</p>
        )}
      </div>
    );
  }

  // 마지막 전송이 접수되지 않았을 때(차단·한도 초과 등)의 안내. 서버 message를 그대로 보여 준다
  const rejectionMessage =
    (hintRequest.state.phase === 'idle' ? hintRequest.state.error?.message : null) ??
    (followupRequest.state.phase === 'idle' ? followupRequest.state.error?.message : null);

  return (
    <SidePanel title="힌트" onClose={onClose}>
      {hintsQuery.isPending && <LoadingIndicator label="힌트 기록을 불러오는 중…" />}
      {hintsQuery.isError && (
        <ErrorMessage
          message={hintsQuery.error.message}
          onRetry={() => void hintsQuery.refetch()}
        />
      )}
      {hintsQuery.isSuccess && hints.length === 0 && !pendingHintInput && (
        <p className="text-sm text-muted">아직 받은 힌트가 없습니다.</p>
      )}

      <ol aria-label="힌트 기록" className="flex flex-col gap-5">
        {hints.map((hint) => (
          <li key={hint.hintId} className="flex flex-col gap-3">
            <QaPair kind="HINT" question={hint.question} answer={hint.content} />
            {renderFollowup(hint)}
          </li>
        ))}
        {pendingHintInput && (
          <li className="flex flex-col gap-3">
            <QaPair kind="HINT" question={pendingHintInput.question} pending />
            {hintRequest.isBusy ? (
              <LoadingIndicator label="답변 생성 중…" />
            ) : (
              <FailureBlock
                message={HINT_FAILED_MESSAGE}
                disabled={!operable}
                onRetry={() => hintRequest.retry()}
              />
            )}
          </li>
        )}
      </ol>
      {(hintRequest.isBusy && pendingHintInput === null) ||
      (followupRequest.isBusy && pendingFollowupInput === null) ? (
        <LoadingIndicator label="이전 질문의 답변을 기다리는 중…" />
      ) : null}
      <div ref={endRef} />

      <div className="mt-auto flex flex-col gap-2 border-t border-line pt-4">
        {rejectionMessage && (
          <p role="alert" className="text-sm text-danger">
            {rejectionMessage}
          </p>
        )}
        {!operable && <p className="text-sm text-muted">{NOT_OPERABLE_MESSAGE}</p>}
        {exhausted && <p className="text-sm text-muted">{EXHAUSTED_MESSAGE}</p>}
        {count !== null && (
          <p className="text-xs text-muted" aria-label="힌트 사용 현황">
            {formatHintUsage(count)}
          </p>
        )}
        <QuestionForm
          label="새 힌트 질문"
          placeholder="힌트가 필요한 점을 질문하세요 (Enter 전송, Shift+Enter 줄바꿈)"
          value={draft}
          onChange={setDraft}
          onSubmit={handleSubmitHint}
          inputDisabled={hintInputDisabled}
          busy={busy}
          submitLabel="힌트 받기"
          notice={
            count === null ? null : `전송하면 힌트 1회를 사용합니다 (남은 ${count.remaining}회)`
          }
        />
      </div>
    </SidePanel>
  );
}
