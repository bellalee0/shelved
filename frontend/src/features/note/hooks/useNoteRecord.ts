import { useQuery } from '@tanstack/react-query';
import { useState } from 'react';
import { getMyNote } from '../api';
import { createDraftStore } from '../draft';

/**
 * 읽기 전용 노트 조회: 서버에 저장된 노트와 이 기기의 미저장 초안.
 * 게임 화면의 useNote와 달리 자동 저장을 만들지 않고, 초안을 지우거나 바꾸지 않는다.
 */
export function useNoteRecord(roomId: number, userId: number) {
  const query = useQuery({
    queryKey: ['note-record', userId, roomId],
    queryFn: () => getMyNote(roomId),
    gcTime: 0,
    refetchOnWindowFocus: false,
  });
  const [draft] = useState(() => createDraftStore(userId, roomId).read());
  // 서버 내용과 같은 초안은 이미 저장된 것이다. 서버 조회에 실패하면 비교할 수 없으므로 보여 준다
  const unsavedDraft = draft !== null && draft.content !== query.data?.content ? draft : null;
  return { query, unsavedDraft };
}
