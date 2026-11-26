/** 남은 횟수 표시에 필요한 값 (UsageCount의 일부) */
type UsageRemaining = {
  remaining: number;
  /** 처리 중 임시 확보 */
  reserved: number;
};

/**
 * "남은 지목 2 (처리 중 1)"처럼 사용 완료와 처리 중을 구분해 보여 준다.
 * HUD(room)와 지목 창(accusation)이 함께 쓰므로 shared에 둔다.
 */
export function formatUsage(label: string, count: UsageRemaining): string {
  const reserved = count.reserved > 0 ? ` (처리 중 ${count.reserved})` : '';
  return `남은 ${label} ${count.remaining}${reserved}`;
}
