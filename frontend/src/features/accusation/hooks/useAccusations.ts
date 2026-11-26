import { useQuery } from '@tanstack/react-query';
import { getAccusations } from '../api';

export function accusationsQueryKey(roomId: number) {
  return ['accusations', roomId] as const;
}

/**
 * 지목 기록·현재 지목 횟수 (GET accusations).
 * 창을 열 때마다(마운트) 서버에서 다시 받는다. 창이 떠 있는 동안 포커스 이동으로 다시 받지는 않는다.
 */
export function useAccusations(roomId: number) {
  return useQuery({
    queryKey: accusationsQueryKey(roomId),
    queryFn: () => getAccusations(roomId),
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
}
