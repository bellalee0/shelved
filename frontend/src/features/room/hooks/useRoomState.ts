import { useQuery } from '@tanstack/react-query';
import { getRoomState } from '../api';

/**
 * 세션 상태를 한 번 읽는 조회 (읽기 전용 화면용). 하트비트·재개·일시정지를 보내지 않는다.
 * 게임 화면의 흐름은 이 훅이 아니라 useGameSession이 맡는다.
 */
export function useRoomState(roomId: number, userId: number) {
  return useQuery({
    queryKey: ['room-state', userId, roomId],
    queryFn: () => getRoomState(roomId),
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
}
