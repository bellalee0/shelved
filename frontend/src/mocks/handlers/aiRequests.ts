import { http } from 'msw';
import { advanceMockAiRequest, findMockAiRequest, toAiRequestStatus } from '../aiRequestDb';
import { loadOwnedRoom } from '../roomAccess';
import { saveMockRoomDb } from '../roomDb';
import { checkScenario } from '../scenarios';
import { mockErrorResponse, ok } from './auth';

// docs/api/room.md 의 GET ai-requests/{requestId}. 일시정지·종료 후에도 조회된다.
// 목의 처리 중 요청은 이 조회가 정해진 횟수만큼 와야 끝난다 (handlers/npc.ts의 npcSlow·npcNoResponse).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const aiRequestHandlers = [
  http.get(`${API_BASE_URL}/api/rooms/:roomId/ai-requests/:requestId`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    const aiRequest = findMockAiRequest(loaded.room, String(params.requestId));
    if (!aiRequest) {
      saveMockRoomDb();
      return mockErrorResponse(404, 'AI_REQUEST_NOT_FOUND', '요청을 찾을 수 없습니다.');
    }
    advanceMockAiRequest(loaded.room, aiRequest);
    saveMockRoomDb();
    return ok(toAiRequestStatus(aiRequest), 'AI 요청 상태 조회 성공');
  }),
];
