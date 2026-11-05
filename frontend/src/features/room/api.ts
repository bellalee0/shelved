import { apiClient } from '../../shared/api/client';
import type { CommonResponse, PageResponse } from '../../shared/api/types';
import type {
  RoomBriefingResponse,
  RoomConnectionResponse,
  RoomCreateRequest,
  RoomCreateResponse,
  RoomListParams,
  RoomMapResponse,
  RoomStateResponse,
  RoomSummaryResponse,
  RoomTimerResponse,
} from './types';

// 함수 이름은 백엔드 RoomController 메서드명과 맞춘다. 명세: docs/api/room.md

/** 내 세션 목록 (최신 생성 순). status로 진행 중(ACTIVE)·종료(ENDED)를 나눠 볼 수 있다 */
export async function getRooms(params: RoomListParams = {}) {
  const response = await apiClient.get<CommonResponse<PageResponse<RoomSummaryResponse>>>(
    '/api/rooms',
    { params },
  );
  return response.data.data;
}

/** 사건 카드를 골라 READY 세션을 만든다. 같은 사건의 미종료 세션이 있으면 409 ROOM_ALREADY_EXISTS(detail.roomId) */
export async function createRoom(request: RoomCreateRequest) {
  const response = await apiClient.post<CommonResponse<RoomCreateResponse>>('/api/rooms', request);
  return response.data.data;
}

export async function getRoomState(roomId: number) {
  const response = await apiClient.get<CommonResponse<RoomStateResponse>>(
    `/api/rooms/${roomId}/state`,
  );
  return response.data.data;
}

export async function getBriefing(roomId: number) {
  const response = await apiClient.get<CommonResponse<RoomBriefingResponse>>(
    `/api/rooms/${roomId}/briefing`,
  );
  return response.data.data;
}

export async function getMap(roomId: number) {
  const response = await apiClient.get<CommonResponse<RoomMapResponse>>(`/api/rooms/${roomId}/map`);
  return response.data.data;
}

/** READY → IN_PROGRESS. 응답의 connectionId를 메모리에 보관(setConnectionId)해 이후 조작 API에 쓴다 */
export async function startRoom(roomId: number) {
  const response = await apiClient.post<CommonResponse<RoomConnectionResponse>>(
    `/api/rooms/${roomId}/start`,
  );
  return response.data.data;
}

/** 접속 확인. X-Connection-Id 필수 */
export async function heartbeat(roomId: number) {
  const response = await apiClient.post<CommonResponse<RoomTimerResponse>>(
    `/api/rooms/${roomId}/heartbeat`,
    undefined,
    { requiresConnectionId: true },
  );
  return response.data.data;
}

/** 일시정지. 새로고침 직후에도 부를 수 있도록 연결 헤더를 요구하지 않으며 중복 호출해도 안전하다 */
export async function pauseRoom(roomId: number) {
  const response = await apiClient.post<CommonResponse<RoomTimerResponse>>(
    `/api/rooms/${roomId}/pause`,
  );
  return response.data.data;
}

/** PAUSED → IN_PROGRESS. 새 connectionId를 발급받는다 */
export async function resumeRoom(roomId: number) {
  const response = await apiClient.post<CommonResponse<RoomConnectionResponse>>(
    `/api/rooms/${roomId}/resume`,
  );
  return response.data.data;
}
