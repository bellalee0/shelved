import { useEffect, useId, useRef, useState, type ReactNode } from 'react';
import { Button } from '../../../shared/components/Button';
import { TRICK_EXPLANATION_MAX_LENGTH } from '../../../shared/constants/accusation';
import {
  ACCUSATION_SUBMIT_BLOCK_MESSAGES,
  countTrickLength,
  type AccusationSubmitBlockReason,
} from '../accusations';
import type { AccusationDraft } from '../types';
import type { AccusationSuspect } from './AccusationRecordList';

type AccusationFormProps = {
  suspects: AccusationSuspect[];
  renderFace?: (suspect: AccusationSuspect) => ReactNode;
  draft: AccusationDraft;
  onDraftChange: (draft: AccusationDraft) => void;
  /** 채점 중이면 입력·대상 선택·제출을 모두 잠근다 */
  locked: boolean;
  /** 지금 제출할 수 없는 이유. null이면 제출할 수 있다 */
  blockReason: AccusationSubmitBlockReason | null;
  /** 남은 지목 기회 (처리 중 확보 반영). 모르면 null */
  remaining: number | null;
  /** 채점에 실패한 지목이 있어 새 답안을 내면 그 지목은 다시 채점할 수 없게 된다 */
  supersedesFailed: boolean;
  /** 확인 단계에서 "제출하기"를 누르면 부른다. 실제로 보냈으면 true */
  onSubmit: () => boolean;
};

/**
 * 범인 선택 + 트릭 설명 입력 + 제출 확인.
 * Enter는 줄바꿈이고 제출하지 않는다(form 요소를 쓰지 않는다). "제출" → 확인 단계 → "제출하기"로만 보낸다.
 */
export function AccusationForm({
  suspects,
  renderFace,
  draft,
  onDraftChange,
  locked,
  blockReason,
  remaining,
  supersedesFailed,
  onSubmit,
}: AccusationFormProps) {
  const [confirming, setConfirming] = useState(false);
  const textareaId = useId();
  const countId = useId();
  const length = countTrickLength(draft.trickExplanation);
  const selected = suspects.find((suspect) => suspect.suspectId === draft.suspectId) ?? null;
  const canSubmit = !locked && blockReason === null;
  const confirmRef = useRef<HTMLDivElement>(null);

  // 확인 상자는 "제출" 버튼보다 커서 창 아래로 밀리므로, 열리면 "제출하기"가 보이도록 아래로 스크롤한다
  // (jsdom에는 scrollIntoView가 없다)
  useEffect(() => {
    if (confirming) {
      confirmRef.current?.scrollIntoView?.({ behavior: 'smooth', block: 'end' });
    }
  }, [confirming]);

  function handleConfirm() {
    if (!canSubmit) {
      return;
    }
    onSubmit();
    setConfirming(false);
  }

  return (
    <div className="flex flex-col gap-3">
      <fieldset className="flex flex-col gap-2" disabled={locked}>
        <legend className="mb-1 text-sm font-bold">범인으로 지목할 인물</legend>
        {suspects.map((suspect) => (
          <label
            key={suspect.suspectId}
            className={`flex cursor-pointer items-center gap-3 rounded-md border px-3 py-2 text-sm ${
              draft.suspectId === suspect.suspectId ? 'border-accent bg-paper' : 'border-line'
            }`}
          >
            <input
              type="radio"
              name={`${textareaId}-suspect`}
              value={suspect.suspectId}
              checked={draft.suspectId === suspect.suspectId}
              onChange={() => {
                setConfirming(false);
                onDraftChange({ ...draft, suspectId: suspect.suspectId });
              }}
            />
            {renderFace?.(suspect)}
            <span className="font-bold">{suspect.name}</span>
          </label>
        ))}
      </fieldset>

      <div className="flex flex-col gap-1">
        <label htmlFor={textareaId} className="text-sm font-bold">
          트릭 설명
        </label>
        <textarea
          id={textareaId}
          aria-describedby={countId}
          rows={8}
          value={draft.trickExplanation}
          disabled={locked}
          placeholder="범인이 어떻게 범행을 저질렀는지 설명하세요 (Enter는 줄바꿈입니다)"
          onChange={(event) => {
            setConfirming(false);
            onDraftChange({ ...draft, trickExplanation: event.target.value });
          }}
          className="resize-y rounded-md border border-line bg-surface px-3 py-2 text-sm leading-relaxed disabled:opacity-60"
        />
        <p
          id={countId}
          className={`self-end text-xs ${length > TRICK_EXPLANATION_MAX_LENGTH ? 'text-danger' : 'text-muted'}`}
        >
          {length} / {TRICK_EXPLANATION_MAX_LENGTH}자
        </p>
      </div>

      {remaining !== null && (
        <p className="text-xs text-muted">
          제출하면 지목 기회 1회를 사용합니다 (남은 {remaining}회). 채점에 실패하면 차감되지
          않습니다.
        </p>
      )}
      {!locked && blockReason !== null && (
        <p className="text-sm text-muted">{ACCUSATION_SUBMIT_BLOCK_MESSAGES[blockReason]}</p>
      )}

      {confirming && selected ? (
        <div
          ref={confirmRef}
          role="group"
          aria-label="제출 확인"
          className="flex flex-col gap-2 rounded-md border border-accent bg-paper px-3 py-3 text-sm"
        >
          <p>
            <span className="font-bold">{selected.name}</span>을(를) 범인으로 지목합니다. 이대로
            제출할까요?
          </p>
          {supersedesFailed && (
            <p className="text-danger">이전 실패한 채점은 다시 할 수 없게 됩니다.</p>
          )}
          <div className="flex gap-2">
            <Button onClick={handleConfirm} disabled={!canSubmit}>
              제출하기
            </Button>
            <Button variant="ghost" onClick={() => setConfirming(false)}>
              취소
            </Button>
          </div>
        </div>
      ) : (
        <Button className="self-start" disabled={!canSubmit} onClick={() => setConfirming(true)}>
          제출
        </Button>
      )}
    </div>
  );
}
