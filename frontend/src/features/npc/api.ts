import { toAiPostResult, type AiPostResult } from '../../shared/api/aiRequests';
import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import { isNpcAskResponse } from './messages';
import type { NpcAskRequest, NpcAskResponse, NpcMessageListResponse } from './types';

// 함수 이름은 백엔드 NpcController 메서드명과 맞춘다. 명세: docs/api/investigation.md

/**
 * NPC에게 질문한다. X-Connection-Id 필수.
 * 200(완료)과 202(같은 요청이 처리 중)를 HTTP 상태로 구분해 돌려준다. 202의 data는 처리 상태다.
 */
export async function askNpc(
  roomId: number,
  suspectId: number,
  request: NpcAskRequest,
): Promise<AiPostResult<NpcAskResponse>> {
  const response = await apiClient.post<CommonResponse<unknown>>(
    `/api/rooms/${roomId}/npc/${suspectId}/ask`,
    request,
    { requiresConnectionId: true },
  );
  return toAiPostResult(response, isNpcAskResponse);
}

/** 한 NPC와의 대화 원문(sequenceNo 순)과 처리 중·실패 요청. 모든 세션 상태에서 조회할 수 있다 */
export async function getNpcMessages(roomId: number, suspectId: number) {
  const response = await apiClient.get<CommonResponse<NpcMessageListResponse>>(
    `/api/rooms/${roomId}/npc/${suspectId}/messages`,
  );
  return response.data.data;
}
