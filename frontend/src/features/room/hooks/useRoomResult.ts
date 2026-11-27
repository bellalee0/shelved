import { useQuery } from '@tanstack/react-query';
import { getResult } from '../api';

/**
 * 종료 세션의 결과(GET result). roomId가 null이면 조회하지 않는다.
 * gcTime 0: 화면을 떠나면 버린다. 종료 전에 받은 409가 캐시에 남아 있으면,
 * 종료 후 다시 들어왔을 때 새 응답이 오기 전에 게임 화면으로 되돌려 보낼 수 있다.
 */
export function useRoomResult(roomId: number | null, userId: number | null) {
  return useQuery({
    queryKey: ['room-result', userId, roomId],
    queryFn: () => getResult(roomId!),
    enabled: roomId !== null && userId !== null,
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}
