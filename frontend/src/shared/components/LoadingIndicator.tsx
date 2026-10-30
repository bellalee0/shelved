type LoadingIndicatorProps = {
  label?: string;
};

/** 데이터를 기다리는 동안 보여주는 표시. 화면 낭독기에도 상태로 알린다 */
export function LoadingIndicator({ label = '불러오는 중…' }: LoadingIndicatorProps) {
  return (
    <div role="status" className="flex items-center gap-3 text-muted">
      <span
        aria-hidden="true"
        className="size-4 animate-spin rounded-full border-2 border-line border-t-accent"
      />
      <span>{label}</span>
    </div>
  );
}
