import { useQuery } from '@tanstack/react-query';
import { getMap } from '../api';

/**
 * 게임 맵 정보(GET /api/rooms/{roomId}/map). 인증이 필요한 API라 userId가 null(비로그인)이거나
 * roomId가 올바르지 않으면 요청하지 않는다. 맵 정보는 세션 중 바뀌지 않으므로 다시 가져오지 않는다.
 */
export function useRoomMap(roomId: number | null, userId: number | null) {
  return useQuery({
    queryKey: ['room-map', userId, roomId],
    queryFn: () => getMap(roomId!),
    enabled: userId !== null && roomId !== null,
    staleTime: Infinity,
  });
}
