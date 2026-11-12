import type { AxiosResponse } from 'axios';
import { apiClient } from './client';
import type { AiRequestStatusResponse, CommonResponse } from './types';

// AI 요청(NPC 질문·힌트·보충 질문·지목)이 함께 쓰는 API 도구 (docs/api/README.md "AI 요청 처리").
// 상태 조회 API는 room 도메인 경로지만 여러 도메인이 쓰므로 shared에 둔다 (features끼리 서로 import하지 않기 위해).

/**
 * AI POST의 결과. HTTP 상태로 구분한다.
 * - completed(200): 완료 결과. 같은 requestId 재전송이면 최초 성공 당시의 스냅샷이다
 * - processing(202): 같은 요청이 아직 처리 중. data는 완료 결과가 아니라 처리 상태다
 */
export type AiPostResult<TResult> =
  { kind: 'completed'; data: TResult } | { kind: 'processing'; data: AiRequestStatusResponse };

/** 응답이 명세와 다를 때 쓰는 문구 (202인데 상태 객체가 아니거나, 200인데 완료 결과가 아님) */
export const UNEXPECTED_AI_RESPONSE_MESSAGE = '서버 응답 형식이 예상과 다릅니다.';

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** 처리 상태 객체인지 최소한으로 확인한다 (requestId·status) */
export function isAiRequestStatusResponse(value: unknown): value is AiRequestStatusResponse {
  return (
    isRecord(value) &&
    typeof value.requestId === 'string' &&
    (value.status === 'PROCESSING' || value.status === 'SUCCEEDED' || value.status === 'FAILED')
  );
}

/**
 * AI POST의 axios 응답을 판별 유니온으로 바꾼다. 202의 data를 완료 타입으로 단정하지 않는다.
 * isCompleted는 도메인의 완료 응답 형식 확인 함수다 (예: NpcAskResponse인지).
 */
export function toAiPostResult<TResult>(
  response: AxiosResponse<CommonResponse<unknown>>,
  isCompleted: (data: unknown) => data is TResult,
): AiPostResult<TResult> {
  const data = response.data.data;
  if (response.status === 202 && isAiRequestStatusResponse(data)) {
    return { kind: 'processing', data };
  }
  if (response.status === 200 && isCompleted(data)) {
    return { kind: 'completed', data };
  }
  throw new Error(UNEXPECTED_AI_RESPONSE_MESSAGE);
}

/**
 * AI 요청 상태 조회 (GET /api/rooms/{roomId}/ai-requests/{requestId}). 백엔드 AiRequestController.
 * 일시정지·종료 후에도 조회된다. 이 세션에 없는 requestId면 404 AI_REQUEST_NOT_FOUND.
 */
export async function getAiRequest(roomId: number, requestId: string) {
  const response = await apiClient.get<CommonResponse<AiRequestStatusResponse>>(
    `/api/rooms/${roomId}/ai-requests/${requestId}`,
  );
  return response.data.data;
}
