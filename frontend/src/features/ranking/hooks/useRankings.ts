import { useQuery } from '@tanstack/react-query';
import { getRankings } from '../api';
import type { RankingGetParams } from '../types';

/**
 * 사건별 랭킹 한 페이지. myRanking이 사용자마다 다르므로 키에 userId를 넣는다.
 * caseId나 userId가 null이면 조회하지 않는다.
 * 같은 사건의 페이지를 넘길 때는 새 페이지가 올 때까지 이전 표를 둔다(isPlaceholderData).
 */
export function useRankings(
  caseId: number | null,
  userId: number | null,
  { page, size }: RankingGetParams,
) {
  return useQuery({
    queryKey: ['rankings', userId, caseId, page, size],
    queryFn: () => getRankings(caseId!, { page, size }),
    enabled: caseId !== null && userId !== null,
    placeholderData: (previous) => (previous?.caseId === caseId ? previous : undefined),
    refetchOnWindowFocus: false,
  });
}
