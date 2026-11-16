import { useId, type KeyboardEvent, type ReactNode } from 'react';
import { Button } from '../../../shared/components/Button';
import { HINT_QUESTION_MAX_LENGTH } from '../../../shared/constants/hint';

type QuestionFormProps = {
  /** 입력창의 접근 가능한 이름 (화면에는 보이지 않는다) */
  label: string;
  placeholder: string;
  value: string;
  onChange: (value: string) => void;
  onSubmit: () => void;
  /** 입력 자체를 막는다 (진행 중이 아님·한도 소진). 막혀 있으면 전송도 못 한다 */
  inputDisabled: boolean;
  /** 답변을 기다리는 중이라 보낼 수 없다 */
  busy: boolean;
  submitLabel: string;
  /** 전송 버튼 위에 항상 보여 줄 안내 ("전송하면 힌트 1회를 사용합니다 (남은 N회)" 등) */
  notice: ReactNode;
};

/** 새 힌트와 보충 질문이 함께 쓰는 입력 영역: 입력창(1~500자, 글자 수), 안내 문구, 전송 버튼 */
export function QuestionForm({
  label,
  placeholder,
  value,
  onChange,
  onSubmit,
  inputDisabled,
  busy,
  submitLabel,
  notice,
}: QuestionFormProps) {
  const textareaId = useId();
  const counterId = `${textareaId}-counter`;
  const question = value.trim();
  const overLimit = question.length > HINT_QUESTION_MAX_LENGTH;
  const canSend = !inputDisabled && !busy && question.length > 0 && !overLimit;

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>) {
    // Enter는 전송, Shift+Enter는 줄바꿈. 한글 조합 중의 Enter는 글자 확정이므로 보내지 않는다
    if (event.key === 'Enter' && !event.shiftKey && !event.nativeEvent.isComposing) {
      event.preventDefault();
      if (canSend) {
        onSubmit();
      }
    }
  }

  return (
    <div className="flex flex-col gap-2">
      <label htmlFor={textareaId} className="sr-only">
        {label}
      </label>
      <textarea
        id={textareaId}
        value={value}
        rows={3}
        disabled={inputDisabled}
        placeholder={placeholder}
        aria-describedby={counterId}
        aria-invalid={overLimit || undefined}
        className={`resize-none rounded-md border bg-surface px-3 py-2 text-sm text-ink outline-none focus:border-accent disabled:opacity-60 ${
          overLimit ? 'border-danger' : 'border-line'
        }`}
        onChange={(event) => onChange(event.target.value)}
        onKeyDown={handleKeyDown}
      />
      <div className="text-xs text-muted">{notice}</div>
      <div className="flex items-center justify-between gap-2">
        <span
          id={counterId}
          className={`text-xs tabular-nums ${overLimit ? 'text-danger' : 'text-muted'}`}
        >
          {question.length}/{HINT_QUESTION_MAX_LENGTH}
        </span>
        <Button disabled={!canSend} onClick={onSubmit}>
          {submitLabel}
        </Button>
      </div>
    </div>
  );
}
