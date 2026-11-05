import { useQuery } from '@tanstack/react-query';
import { getRooms } from '../api';
import type { RoomListStatus } from '../types';

/** 메인 화면 목록에서 한 번에 가져올 세션 수. 사건이 3편뿐이라 한 페이지로 충분하다 */
const ROOM_LIST_SIZE = 100;

/**
 * 내 세션 목록(진행 중 ACTIVE / 종료 ENDED).
 * 인증이 필요한 API라 userId가 null(비로그인)이면 요청하지 않는다 —
 * 비로그인 상태에서 부르면 401 LOGIN_REQUIRED로 로그아웃 처리가 돌 수 있다.
 * 쿼리 키에 userId를 넣어 다른 사용자의 캐시가 섞이지 않게 한다.
 */
export function useRooms(status: RoomListStatus, userId: number | null) {
  return useQuery({
    queryKey: ['rooms', userId, status],
    queryFn: () => getRooms({ status, size: ROOM_LIST_SIZE }),
    enabled: userId !== null,
  });
}
