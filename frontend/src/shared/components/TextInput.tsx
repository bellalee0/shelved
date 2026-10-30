import { useId, type InputHTMLAttributes } from 'react';

type TextInputProps = Omit<InputHTMLAttributes<HTMLInputElement>, 'id'> & {
  label: string;
  /** 입력 규칙 등 항상 보여줄 안내 문구 */
  hint?: string;
  /** 오류 문구. 있으면 hint 대신 오류로 표시하고 aria-invalid를 켠다 */
  error?: string | null;
};

/** 라벨·안내·오류 문구가 연결된 한 줄 입력창. 라벨과 입력창은 id로 자동 연결된다 */
export function TextInput({ label, hint, error, className = '', ...rest }: TextInputProps) {
  const inputId = useId();
  const messageId = `${inputId}-message`;
  const message = error ?? hint;

  return (
    <div className={`flex flex-col gap-1 ${className}`}>
      <label htmlFor={inputId} className="text-sm font-medium">
        {label}
      </label>
      <input
        id={inputId}
        type="text"
        aria-invalid={error ? true : undefined}
        aria-describedby={message ? messageId : undefined}
        className={`rounded-md border bg-surface px-3 py-2 text-ink outline-none focus:border-accent ${
          error ? 'border-danger' : 'border-line'
        }`}
        {...rest}
      />
      {message && (
        <p id={messageId} className={`text-sm ${error ? 'text-danger' : 'text-muted'}`}>
          {message}
        </p>
      )}
    </div>
  );
}
