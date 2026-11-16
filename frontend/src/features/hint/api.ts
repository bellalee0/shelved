import { toAiPostResult, type AiPostResult } from '../../shared/api/aiRequests';
import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import { isHintCreateResponse, isHintFollowupResponse } from './hints';
import type {
  HintCreateRequest,
  HintCreateResponse,
  HintFollowupRequest,
  HintFollowupResponse,
  HintListResponse,
} from './types';

// 함수 이름은 백엔드 HintController 메서드명과 맞춘다. 명세: docs/api/hint-accusation.md

/** 힌트 기록과 처리 중·실패 요청, 현재 힌트 횟수. 모든 세션 상태에서 조회할 수 있고 횟수를 차감하지 않는다 */
export async function getHints(roomId: number) {
  const response = await apiClient.get<CommonResponse<HintListResponse>>(
    `/api/rooms/${roomId}/hints`,
  );
  return response.data.data;
}

/** 새 힌트를 요청한다. X-Connection-Id 필수. 200(완료)과 202(처리 중)를 HTTP 상태로 구분해 돌려준다 */
export async function createHint(
  roomId: number,
  request: HintCreateRequest,
): Promise<AiPostResult<HintCreateResponse>> {
  const response = await apiClient.post<CommonResponse<unknown>>(
    `/api/rooms/${roomId}/hints`,
    request,
    { requiresConnectionId: true },
  );
  return toAiPostResult(response, isHintCreateResponse);
}

/** 힌트에 대한 무료 보충 질문. X-Connection-Id 필수 */
export async function createHintFollowup(
  roomId: number,
  hintId: number,
  request: HintFollowupRequest,
): Promise<AiPostResult<HintFollowupResponse>> {
  const response = await apiClient.post<CommonResponse<unknown>>(
    `/api/rooms/${roomId}/hints/${hintId}/followup`,
    request,
    { requiresConnectionId: true },
  );
  return toAiPostResult(response, isHintFollowupResponse);
}
