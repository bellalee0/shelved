import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import type { NoteResponse, NoteUpdateRequest } from './types';

/** 내 개인 노트 조회. 일시정지·종료 후에도 조회할 수 있다 */
export async function getMyNote(roomId: number) {
  const response = await apiClient.get<CommonResponse<NoteResponse>>(
    `/api/rooms/${roomId}/notes/me`,
  );
  return response.data.data;
}

/** 노트 전체 저장. X-Connection-Id 필요. revision이 다르면 409 NOTE_REVISION_CONFLICT(detail에 서버 내용) */
export async function updateMyNote(roomId: number, request: NoteUpdateRequest) {
  const response = await apiClient.put<CommonResponse<NoteResponse>>(
    `/api/rooms/${roomId}/notes/me`,
    request,
    { requiresConnectionId: true },
  );
  return response.data.data;
}
