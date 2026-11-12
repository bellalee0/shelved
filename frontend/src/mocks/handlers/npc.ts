import { delay, http, HttpResponse } from 'msw';
import type { NpcAskRequest, NpcMessageListResponse } from '../../features/npc/types';
import { NPC_QUESTION_MAX_LENGTH } from '../../shared/constants/npc';
import {
  acceptNpcAsk,
  findMockAiRequest,
  findProcessingNpcAsk,
  finishMockAiRequest,
  npcMessagesFor,
  pendingAiRequestsOf,
  toAiRequestStatus,
} from '../aiRequestDb';
import { findMockCase } from '../data/clocktowerCase';
import { checkOperable, loadOwnedRoom } from '../roomAccess';
import { saveMockRoomDb, type MockRoom } from '../roomDb';
import { checkScenario, getEnabledScenarios } from '../scenarios';
import { mockErrorResponse, ok } from './auth';

// docs/api/investigation.md 의 NPC 질문·대화 기록. requestId 규칙은 docs/api/README.md "AI 요청 처리".
//
// 개발 중 재현 방법 (스위치는 window.shelvedMock.setScenario로 켠다)
// - 기본: 약 1초 뒤 200 답변
// - npcSlow: 첫 POST가 202(처리 중), 상태 조회 두 번째에 완료
// - npcFail: 첫 실행은 502 NPC_ANSWER_FAILED, 같은 requestId로 다시 보내면 성공 (npcSlow와 함께 켜면 조회 결과가 FAILED)
// - npcNoResponse: 서버는 접수했지만 응답 없이 연결이 끊김, 상태 조회 첫 번째에 완료
// - 질문에 MOCK_BLOCKED_KEYWORD가 들어 있으면 400 QUESTION_BLOCKED (기록되지 않음)
// - 같은 requestId에 다른 질문이면 409 AI_REQUEST_ID_REUSED, 완료된 requestId 재전송은 저장된 결과 200

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/** 이 낱말이 들어 있는 질문은 직접적인 정답 요구로 보고 차단한다 (목 전용 테스트 문구) */
export const MOCK_BLOCKED_KEYWORD = '범인';

/** 정상 답변까지의 지연(ms). 테스트에서는 기다리지 않는다 */
const MOCK_ANSWER_DELAY_MS = import.meta.env.MODE === 'test' ? 0 : 1000;
/** npcSlow: 완료까지 필요한 상태 조회 횟수 */
const SLOW_POLLS_UNTIL_DONE = 2;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

function findSuspect(room: MockRoom, suspectId: number) {
  return findMockCase(room.caseId)?.suspects.find((suspect) => suspect.suspectId === suspectId);
}

function suspectNotFound() {
  return mockErrorResponse(404, 'SUSPECT_NOT_FOUND', '인물이 존재하지 않습니다.');
}

function npcAnswerFailed(requestId: string) {
  return mockErrorResponse(
    502,
    'NPC_ANSWER_FAILED',
    'NPC의 답변을 받지 못했습니다. 같은 질문을 다시 보내 주세요.',
    { requestId },
  );
}

export const npcHandlers = [
  http.post(`${API_BASE_URL}/api/rooms/:roomId/npc/:suspectId/ask`, async ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    const { room } = loaded;
    const suspectId = Number(params.suspectId);

    const body = (await request.json().catch(() => ({}))) as Partial<NpcAskRequest>;
    if (typeof body.requestId !== 'string' || !UUID_PATTERN.test(body.requestId)) {
      return mockErrorResponse(400, 'INVALID_INPUT', '요청 식별자 형식이 올바르지 않습니다.', {
        field: 'requestId',
      });
    }
    if (
      typeof body.question !== 'string' ||
      body.question.trim().length === 0 ||
      body.question.length > NPC_QUESTION_MAX_LENGTH
    ) {
      return mockErrorResponse(
        400,
        'INVALID_INPUT',
        `질문은 1~${NPC_QUESTION_MAX_LENGTH}자로 입력해 주세요.`,
        { field: 'question' },
      );
    }
    const { requestId, question } = body;

    // 같은 requestId: 입력이 다르면 거부, 완료면 저장된 결과(연결 검사 없음), 처리 중이면 202, 실패면 다시 실행
    const existing = findMockAiRequest(room, requestId);
    if (existing) {
      if (existing.targetId !== suspectId || existing.input.question !== question) {
        return mockErrorResponse(
          409,
          'AI_REQUEST_ID_REUSED',
          '같은 요청 식별자로 다른 내용을 보낼 수 없습니다.',
        );
      }
      if (existing.status === 'SUCCEEDED') {
        return ok(existing.result, 'NPC 질문 성공');
      }
      if (existing.status === 'PROCESSING') {
        return ok(toAiRequestStatus(existing), '요청을 처리하고 있습니다.', 202);
      }
    }

    const operableError = checkOperable(request, loaded);
    if (operableError) {
      return operableError;
    }
    const suspect = findSuspect(room, suspectId);
    if (!suspect) {
      return suspectNotFound();
    }
    if (!suspect.canConverse) {
      return mockErrorResponse(400, 'NPC_NOT_CONVERSABLE', '대화할 수 없는 인물입니다.');
    }
    if (question.includes(MOCK_BLOCKED_KEYWORD)) {
      // 접수 전에 차단하므로 기록하지 않는다
      return mockErrorResponse(
        400,
        'QUESTION_BLOCKED',
        '범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다. 인물의 행적, 목격한 사실, 물증에 관해 질문해 보세요.',
      );
    }
    const processing = findProcessingNpcAsk(room, suspectId);
    if (processing) {
      return mockErrorResponse(
        409,
        'AI_REQUEST_IN_PROGRESS',
        '이전 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.',
        { requestId: processing.requestId },
      );
    }

    const scenarios = getEnabledScenarios();
    const pollsUntilDone = scenarios.includes('npcSlow')
      ? SLOW_POLLS_UNTIL_DONE
      : scenarios.includes('npcNoResponse')
        ? 1
        : null;
    const aiRequest = acceptNpcAsk(room, {
      requestId,
      suspectId,
      question,
      pollsUntilDone,
      failFirstAttempt: scenarios.includes('npcFail'),
    });
    saveMockRoomDb();

    if (scenarios.includes('npcSlow')) {
      return ok(toAiRequestStatus(aiRequest), '요청을 처리하고 있습니다.', 202);
    }
    if (scenarios.includes('npcNoResponse')) {
      return HttpResponse.error();
    }

    await delay(MOCK_ANSWER_DELAY_MS);
    finishMockAiRequest(room, aiRequest);
    saveMockRoomDb();
    if (aiRequest.status === 'FAILED') {
      return npcAnswerFailed(requestId);
    }
    return ok(aiRequest.result, 'NPC 질문 성공');
  }),

  // 조회는 모든 세션 상태에서 가능하다
  http.get(`${API_BASE_URL}/api/rooms/:roomId/npc/:suspectId/messages`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    const { room } = loaded;
    const suspect = findSuspect(room, Number(params.suspectId));
    if (!suspect) {
      return suspectNotFound();
    }
    const data: NpcMessageListResponse = {
      suspectId: suspect.suspectId,
      suspectName: suspect.name,
      messages: npcMessagesFor(room, suspect.suspectId),
      pendingRequests: pendingAiRequestsOf(room, suspect.suspectId),
    };
    return ok(data, 'NPC 대화 기록 조회 성공');
  }),
];
