import { toAiPostResult, type AiPostResult } from '../../shared/api/aiRequests';
import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import { isAccusationCreateResponse } from './accusations';
import type {
  AccusationCreateRequest,
  AccusationCreateResponse,
  AccusationListResponse,
} from './types';

// 함수 이름은 백엔드 AccusationController 메서드명과 맞춘다. 명세: docs/api/hint-accusation.md "지목"

/** 지목 기록과 현재 지목 횟수 (오래된 순). 모든 세션 상태에서 조회할 수 있다 */
export async function getAccusations(roomId: number) {
  const response = await apiClient.get<CommonResponse<AccusationListResponse>>(
    `/api/rooms/${roomId}/accusations`,
  );
  return response.data.data;
}

/**
 * 범인 지목을 제출한다. 같은 엔드포인트가 새 답안과 실패한 채점의 재채점을 함께 받는다.
 * - 새 답안(isRetry=false): X-Connection-Id 필수
 * - 재채점(isRetry=true): 같은 requestId·같은 답안이며 연결 헤더를 붙이지 않는다.
 *   일시정지·마감 후에도 보내야 하는데 그때는 유효한 연결이 없기 때문이다
 * 200(완료)과 202(처리 중)를 HTTP 상태로 구분해 돌려준다.
 */
export async function createAccusation(
  roomId: number,
  request: AccusationCreateRequest,
  { isRetry }: { isRetry: boolean },
): Promise<AiPostResult<AccusationCreateResponse>> {
  const response = await apiClient.post<CommonResponse<unknown>>(
    `/api/rooms/${roomId}/accusations`,
    request,
    { requiresConnectionId: !isRetry },
  );
  return toAiPostResult(response, isAccusationCreateResponse);
}
