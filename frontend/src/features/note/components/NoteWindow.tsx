import { useEffect, useId, type ReactNode } from 'react';
import { Button } from '../../../shared/components/Button';
import type { NoteSaveStatus } from '../autosave';
import type { NoteHandle } from '../hooks/useNote';

type NoteWindowProps = {
  note: NoteHandle;
  onClose: () => void;
};

const STATUS_LABELS: Record<NoteSaveStatus, string> = {
  LOADING: '불러오는 중…',
  SAVED: '저장됨',
  SAVING: '저장 중…',
  DIRTY: '저장 안 된 변경 있음',
  FAILED: '저장 실패',
  CONFLICT: '충돌: 서버에 다른 내용이 있습니다',
  BLOCKED: '저장 불가',
  TOO_LONG: '글자 수 초과',
};

const READ_ONLY_REASON = '수사 진행 중에만 편집할 수 있습니다.';

/** 이 기기에서 복사하기. 클립보드가 막혀 있으면 조용히 넘어간다 */
function copyToClipboard(text: string) {
  void navigator.clipboard?.writeText(text).catch(() => undefined);
}

function DraftBox({
  title,
  content,
  children,
}: {
  title: string;
  content: string;
  children: ReactNode;
}) {
  return (
    <div className="flex flex-col gap-2 rounded-md border border-line bg-paper p-3">
      <p className="text-sm font-medium">{title}</p>
      <pre className="max-h-32 overflow-auto text-sm break-words whitespace-pre-wrap">
        {content}
      </pre>
      <div className="flex flex-wrap gap-2">{children}</div>
    </div>
  );
}

/**
 * 개인 노트 창의 내용. 창을 열고 닫는 일은 게임 화면이 맡는다.
 * 닫을 때(닫기 버튼, 또는 창이 사라질 때) 저장 안 된 내용을 저장 시도하며, 실패해도 입력은 남는다.
 */
export function NoteWindow({ note, onClose }: NoteWindowProps) {
  const textareaId = useId();
  const { state, flush } = note;

  // 창이 어떤 경로로 닫혀도(Esc 포함) 미저장 내용을 저장 시도한다. 노트 상태는 창이 닫혀도 남아 있다
  useEffect(
    () => () => {
      void flush();
    },
    [flush],
  );

  function handleClose() {
    void flush();
    onClose();
  }

  const awaitingDraftChoice = state.pendingDraft !== null;
  const readOnly = !state.operable || awaitingDraftChoice;
  const overLimit = state.content.length > state.maxLength;

  return (
    <section
      aria-label="개인 노트"
      className="flex w-80 flex-col gap-3 rounded-md border border-line bg-surface p-4"
    >
      <header className="flex items-center justify-between">
        <h2 className="text-base font-semibold">개인 노트</h2>
        <Button variant="ghost" onClick={handleClose}>
          닫기
        </Button>
      </header>

      {note.loadStatus === 'loading' && <p className="text-sm text-muted">노트를 불러오는 중…</p>}

      {note.loadStatus === 'error' && (
        <div className="flex flex-col gap-2">
          <p role="alert" className="text-sm text-danger">
            {note.loadErrorMessage}
          </p>
          <Button variant="secondary" onClick={note.reload}>
            다시 불러오기
          </Button>
        </div>
      )}

      {note.loadStatus === 'ready' && (
        <>
          {state.pendingDraft && (
            <DraftBox title="이 기기의 미저장 초안" content={state.pendingDraft.content}>
              <Button
                variant="secondary"
                onClick={() => copyToClipboard(state.pendingDraft!.content)}
              >
                복사
              </Button>
              <Button
                variant="secondary"
                disabled={!state.operable}
                onClick={note.continueWithDraft}
              >
                초안으로 이어 쓰기
              </Button>
              <Button variant="ghost" onClick={note.discardDraft}>
                초안 버리기
              </Button>
            </DraftBox>
          )}

          {state.conflict && (
            <div
              role="alert"
              className="flex flex-col gap-2 rounded-md border border-danger bg-danger-soft p-3"
            >
              <p className="text-sm font-medium text-danger">
                다른 곳에서 노트가 먼저 저장되었습니다. 어느 쪽을 쓸지 골라 주세요.
              </p>
              <DraftBox title="서버에 저장된 내용" content={state.conflict.currentContent}>
                <Button variant="secondary" onClick={note.resolveWithServer}>
                  서버 내용으로 바꾸기
                </Button>
              </DraftBox>
              <DraftBox title="내 초안" content={state.content}>
                <Button variant="secondary" onClick={() => copyToClipboard(state.content)}>
                  복사
                </Button>
                <Button disabled={!state.operable} onClick={note.overwriteWithMine}>
                  내 초안으로 덮어쓰기
                </Button>
              </DraftBox>
            </div>
          )}

          <div className="flex flex-col gap-1">
            <label htmlFor={textareaId} className="sr-only">
              노트 내용
            </label>
            <textarea
              id={textareaId}
              value={state.content}
              readOnly={readOnly}
              rows={10}
              aria-invalid={overLimit || undefined}
              onChange={(event) => note.setContent(event.target.value)}
              className="resize-y rounded-md border border-line bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent"
            />
            <div className="flex items-center justify-between text-sm">
              <span
                aria-live="polite"
                className={state.status === 'FAILED' ? 'text-danger' : 'text-muted'}
              >
                {STATUS_LABELS[state.status]}
              </span>
              <span className={overLimit ? 'text-danger' : 'text-muted'}>
                {state.content.length} / {state.maxLength}
              </span>
            </div>
          </div>

          {overLimit && (
            <p role="alert" className="text-sm text-danger">
              {state.maxLength}자를 넘어서 저장되지 않습니다. 내용을 줄여 주세요.
            </p>
          )}
          {state.status === 'TOO_LONG' && !overLimit && (
            <p role="alert" className="text-sm text-danger">
              노트가 너무 길어 저장되지 않았습니다. 내용을 줄여 주세요.
            </p>
          )}
          {state.status === 'BLOCKED' && (
            <p role="alert" className="text-sm text-danger">
              지금은 저장할 수 없습니다. 입력한 내용은 이 기기에 남아 있습니다.
            </p>
          )}
          {!state.operable && <p className="text-sm text-muted">{READ_ONLY_REASON}</p>}
          {awaitingDraftChoice && (
            <p className="text-sm text-muted">
              이 기기의 미저장 초안을 "초안으로 이어 쓰기" 또는 "초안 버리기" 중 하나로 먼저 정해
              주세요.
            </p>
          )}
          {state.status === 'FAILED' && (
            <Button variant="secondary" onClick={note.retry}>
              다시 시도
            </Button>
          )}
        </>
      )}
    </section>
  );
}
