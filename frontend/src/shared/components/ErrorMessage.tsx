import { Button } from './Button';

type ErrorMessageProps = {
  /** 사용자에게 보여줄 문구. 백엔드 응답이 있으면 그 message를 그대로 넘긴다 */
  message: string;
  /** 있으면 "다시 시도" 버튼을 보여준다 */
  onRetry?: () => void;
};

/** 요청 실패 등 오류 안내. 나타나는 즉시 화면 낭독기가 읽도록 role="alert"를 쓴다 */
export function ErrorMessage({ message, onRetry }: ErrorMessageProps) {
  return (
    <div
      role="alert"
      className="flex items-center justify-between gap-4 rounded-md bg-danger-soft px-4 py-3 text-sm text-danger"
    >
      <p>{message}</p>
      {onRetry && (
        <Button variant="secondary" onClick={onRetry}>
          다시 시도
        </Button>
      )}
    </div>
  );
}
