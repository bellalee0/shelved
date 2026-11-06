import type { UsageCount } from './types';

/** "남은 지목 2 (처리 중 1)"처럼 사용 완료와 처리 중을 구분해 보여 준다 */
export function formatUsage(label: string, count: UsageCount): string {
  const reserved = count.reserved > 0 ? ` (처리 중 ${count.reserved})` : '';
  return `남은 ${label} ${count.remaining}${reserved}`;
}
