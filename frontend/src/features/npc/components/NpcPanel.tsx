import { useEffect, useId, useRef, useState, type KeyboardEvent } from 'react';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { SidePanel } from '../../../shared/components/SidePanel';
import { NPC_QUESTION_MAX_LENGTH } from '../../../shared/constants/npc';
import { useNpcChat } from '../hooks/useNpcChat';
import { MessageBubble, NpcMessageList } from './NpcMessageList';

export type NpcPanelSuspect = {
  suspectId: number;
  /** 맵 정보(suspects)의 표시 이름 */
  name: string;
  /** false면 질문을 보낼 수 없다 (서버도 거부한다) */
  canConverse: boolean;
};

type NpcPanelProps = {
  roomId: number;
  suspect: NpcPanelSuspect;
  /** 지금 질문을 보낼 수 있는 세션 상태인지 (진행 중·유효한 연결·남은 시간 > 0). 아니면 기록 읽기만 한다 */
  operable: boolean;
  /** 재접속할 때마다 바뀌는 값. 바뀌면 대화 기록을 다시 받는다 */
  resyncKey: number;
  /** 새 답변이 완료됐을 때 (게임 화면이 GET state를 다시 조회한다) */
  onAnswered?: () => void;
  /** 세션 상태 때문에 질문이 거부됐을 때의 오류 코드 */
  onSessionRejected: (code: string) => void;
  onClose: () => void;
};

const NOT_OPERABLE_MESSAGE =
  '수사가 진행 중일 때만 질문할 수 있습니다. 대화 기록은 읽을 수 있습니다.';
const NOT_CONVERSABLE_MESSAGE = '이 인물과는 대화할 수 없습니다.';

/**
 * NPC 대화창. 열 때 대화 기록을 불러오고, 질문을 보내 답변을 받는다.
 * 창을 닫아도 서버의 요청은 취소되지 않는다. 다시 열면 기록의 pendingRequests로 "답변 생성 중"·"다시 보내기"를 복원한다.
 * Esc·창 여닫기는 게임 화면의 창 관리가 맡고, 입력창에 포커스가 있으면 Phaser는 키를 무시한다.
 */
export function NpcPanel({
  roomId,
  suspect,
  operable,
  resyncKey,
  onAnswered,
  onSessionRejected,
  onClose,
}: NpcPanelProps) {
  const textareaId = useId();
  const counterId = `${textareaId}-counter`;
  const [draft, setDraft] = useState('');
  const endRef = useRef<HTMLDivElement>(null);

  const { messagesQuery, request } = useNpcChat({
    roomId,
    suspectId: suspect.suspectId,
    resyncKey,
    onAnswered,
    // 접수되지 않은 질문(차단 등)은 입력창에 되돌린다. 그사이 새로 입력한 내용이 있으면 덮어쓰지 않는다
    onRejected: (input) => setDraft((current) => (current === '' ? input.question : current)),
    onSessionRejected,
  });
  const { state, isBusy } = request;
  const messages = messagesQuery.data?.messages ?? [];

  // 새 말풍선이 생기면 아래로 내린다 (jsdom에는 scrollIntoView가 없다)
  useEffect(() => {
    endRef.current?.scrollIntoView?.({ block: 'end' });
  }, [messages.length, state.phase]);

  const question = draft.trim();
  const overLimit = question.length > NPC_QUESTION_MAX_LENGTH;
  const inputEnabled = suspect.canConverse && operable;
  const canSend = inputEnabled && !isBusy && question.length > 0 && !overLimit;

  function handleSubmit() {
    if (canSend && request.submit({ question })) {
      setDraft('');
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter는 전송, Shift+Enter는 줄바꿈. 한글 조합 중의 Enter는 글자 확정이므로 보내지 않는다
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      handleSubmit();
    }
  }

  const pendingQuestion = state.phase !== 'idle' ? state.input?.question : undefined;

  return (
    <SidePanel title={suspect.name} onClose={onClose}>
      {messagesQuery.isPending && <LoadingIndicator label="대화 기록을 불러오는 중…" />}
      {messagesQuery.isError && (
        <ErrorMessage
          message={messagesQuery.error.message}
          onRetry={() => void messagesQuery.refetch()}
        />
      )}
      {messagesQuery.isSuccess && messages.length === 0 && state.phase === 'idle' && (
        <p className="text-sm text-muted">아직 나눈 대화가 없습니다.</p>
      )}

      <NpcMessageList messages={messages} suspectName={suspect.name}>
        {pendingQuestion !== undefined && (
          <MessageBubble senderType="USER" label="나" pending>
            {pendingQuestion}
          </MessageBubble>
        )}
      </NpcMessageList>

      {(state.phase === 'sending' || state.phase === 'processing') && (
        <LoadingIndicator
          label={
            pendingQuestion === undefined ? '이전 질문의 답변을 기다리는 중…' : '답변 생성 중…'
          }
        />
      )}

      {state.phase === 'failed' && state.error && (
        <div
          role="alert"
          className="flex flex-col gap-2 rounded-md bg-danger-soft px-4 py-3 text-sm text-danger"
        >
          <p>{state.error.message}</p>
          <Button
            variant="secondary"
            className="self-start"
            disabled={!inputEnabled}
            onClick={() => request.retry()}
          >
            다시 보내기
          </Button>
        </div>
      )}
      <div ref={endRef} />

      <div className="mt-auto flex flex-col gap-2 border-t border-line pt-4">
        {!suspect.canConverse ? (
          <p className="text-sm text-muted">{NOT_CONVERSABLE_MESSAGE}</p>
        ) : (
          <>
            {state.phase === 'idle' && state.error && (
              <p role="alert" className="text-sm text-danger">
                {state.error.message}
              </p>
            )}
            {!operable && <p className="text-sm text-muted">{NOT_OPERABLE_MESSAGE}</p>}
            <label htmlFor={textareaId} className="sr-only">
              질문
            </label>
            <textarea
              id={textareaId}
              value={draft}
              rows={3}
              disabled={!inputEnabled}
              placeholder="질문을 입력하세요 (Enter 전송, Shift+Enter 줄바꿈)"
              aria-describedby={counterId}
              aria-invalid={overLimit || undefined}
              className={`resize-none rounded-md border bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent disabled:opacity-60 ${
                overLimit ? 'border-danger' : 'border-line'
              }`}
              onChange={(event) => setDraft(event.target.value)}
              onKeyDown={handleKeyDown}
            />
            <div className="flex items-center justify-between gap-2">
              <span
                id={counterId}
                className={`text-xs tabular-nums ${overLimit ? 'text-danger' : 'text-muted'}`}
              >
                {question.length}/{NPC_QUESTION_MAX_LENGTH}
              </span>
              <Button disabled={!canSend} onClick={handleSubmit}>
                보내기
              </Button>
            </div>
          </>
        )}
      </div>
    </SidePanel>
  );
}
