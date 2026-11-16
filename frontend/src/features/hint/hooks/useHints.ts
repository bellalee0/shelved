import { useQuery } from '@tanstack/react-query';
import { getHints } from '../api';

export function hintsQueryKey(roomId: number) {
  return ['hints', roomId] as const;
}

/**
 * 힌트 기록·현재 힌트 횟수·처리 중 요청 (GET hints).
 * 창을 열 때마다(마운트) 서버에서 다시 받는다. 창이 떠 있는 동안 포커스 이동으로 다시 받지는 않는다.
 */
export function useHints(roomId: number) {
  return useQuery({
    queryKey: hintsQueryKey(roomId),
    queryFn: () => getHints(roomId),
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
}
