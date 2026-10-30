import type { ButtonHTMLAttributes } from 'react';

type ButtonVariant = 'primary' | 'secondary' | 'ghost';

type ButtonProps = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: ButtonVariant;
  /** true면 버튼을 비활성화하고 문구 대신 처리 중 표시를 보여준다 */
  isLoading?: boolean;
};

const VARIANT_CLASS_NAMES: Record<ButtonVariant, string> = {
  primary: 'bg-accent text-surface hover:bg-accent-strong',
  secondary: 'border border-line bg-surface text-ink hover:border-accent',
  ghost: 'text-ink hover:bg-line/40',
};

/**
 * 공통 버튼. type을 지정하지 않으면 "button"이다.
 * (HTML 기본값은 "submit"이라 form 안에 두면 의도치 않게 제출되므로, 제출 버튼만 type="submit"을 명시한다)
 */
export function Button({
  variant = 'primary',
  isLoading = false,
  type = 'button',
  disabled,
  className = '',
  children,
  ...rest
}: ButtonProps) {
  return (
    <button
      type={type}
      disabled={disabled || isLoading}
      aria-busy={isLoading || undefined}
      className={`inline-flex items-center justify-center gap-2 rounded-md px-4 py-2 text-sm font-medium transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${VARIANT_CLASS_NAMES[variant]} ${className}`}
      {...rest}
    >
      {isLoading ? '처리 중…' : children}
    </button>
  );
}
