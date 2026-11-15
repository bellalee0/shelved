import { useQuery } from '@tanstack/react-query';
import { getNpcMessages } from '../api';

/** NPC 대화 기록의 쿼리 키. 대화창과 대화 기록 패널이 같은 키를 써서 한 곳(쿼리 캐시)에서 관리한다 */
export function npcMessagesQueryKey(roomId: number, suspectId: number) {
  return ['npcMessages', roomId, suspectId] as const;
}

/**
 * 한 NPC와의 대화 원문과 처리 중·실패 요청 (GET messages).
 * 창을 열 때마다(마운트) 서버에서 다시 받는다. 창이 떠 있는 동안 포커스 이동으로 다시 받지는 않는다.
 * suspectId가 null이면 조회하지 않는다 (대화 기록 패널에서 아직 인물을 고르지 않음).
 */
export function useNpcMessages(roomId: number, suspectId: number | null) {
  return useQuery({
    queryKey: npcMessagesQueryKey(roomId, suspectId ?? 0),
    queryFn: () => getNpcMessages(roomId, suspectId!),
    enabled: suspectId !== null,
    staleTime: 0,
    refetchOnWindowFocus: false,
  });
}
