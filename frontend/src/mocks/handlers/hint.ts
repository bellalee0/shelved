import { delay, http, HttpResponse } from 'msw';
import type { HintCreateRequest, HintListResponse } from '../../features/hint/types';
import { HINT_QUESTION_MAX_LENGTH } from '../../shared/constants/hint';
import {
  findMockAiRequest,
  pendingAiRequestsOf,
  toAiRequestStatus,
  finishMockAiRequest,
} from '../aiRequestDb';
import {
  acceptHintRequest,
  findMockHint,
  findProcessingFollowup,
  findProcessingHint,
  hintCountOf,
  hintsFor,
} from '../hintDb';
import { checkOperable, loadOwnedRoom } from '../roomAccess';
import { saveMockRoomDb, type MockRoom } from '../roomDb';
import { checkScenario, getEnabledScenarios } from '../scenarios';
import { mockErrorResponse, ok } from './auth';
import { MOCK_BLOCKED_KEYWORD } from './npc';

// docs/api/hint-accusation.md 의 힌트 기록·새 힌트·보충 질문. requestId 규칙은 docs/api/README.md "AI 요청 처리".
//
// 개발 중 재현 방법 (스위치는 window.shelvedMock.setScenario로 켠다. 새 힌트·보충 질문에 똑같이 적용된다)
// - 기본: 약 1초 뒤 200 답변
// - hintSlow: 첫 POST가 202(처리 중), 상태 조회 두 번째에 완료
// - hintFail: 첫 실행은 502(차감·사용 처리 없음), 같은 requestId로 다시 보내면 성공 (hintSlow와 함께 켜면 조회 결과가 FAILED)
// - hintNoResponse: 서버는 접수했지만 응답 없이 연결이 끊김, 상태 조회 첫 번째에 완료
// - 질문에 MOCK_BLOCKED_KEYWORD가 들어 있으면 400 QUESTION_BLOCKED (기록·차감 없음)
// - 힌트 한도(난이도별)를 넘으면 409 HINT_LIMIT_EXCEEDED. 처리 중인 새 힌트도 한도에 포함한다
// - 같은 requestId에 다른 질문이면 409 AI_REQUEST_ID_REUSED, 완료된 requestId 재전송은 저장된 결과 200

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/** 정상 답변까지의 지연(ms). 테스트에서는 기다리지 않는다 */
const MOCK_ANSWER_DELAY_MS = import.meta.env.MODE === 'test' ? 0 : 1000;
/** hintSlow: 완료까지 필요한 상태 조회 횟수 */
const SLOW_POLLS_UNTIL_DONE = 2;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

const BLOCKED_MESSAGE =
  '범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다. 인물의 행적, 목격한 사실, 물증에 관해 질문해 보세요.';

type ParsedBody = { requestId: string; question: string } | { error: Response };

async function parseBody(request: Request): Promise<ParsedBody> {
  const body = (await request.json().catch(() => ({}))) as Partial<HintCreateRequest>;
  if (typeof body.requestId !== 'string' || !UUID_PATTERN.test(body.requestId)) {
    return {
      error: mockErrorResponse(400, 'INVALID_INPUT', '요청 식별자 형식이 올바르지 않습니다.', {
        field: 'requestId',
      }),
    };
  }
  if (
    typeof body.question !== 'string' ||
    body.question.trim().length === 0 ||
    body.question.length > HINT_QUESTION_MAX_LENGTH
  ) {
    return {
      error: mockErrorResponse(
        400,
        'INVALID_INPUT',
        `질문은 1~${HINT_QUESTION_MAX_LENGTH}자로 입력해 주세요.`,
        { field: 'question' },
      ),
    };
  }
  return { requestId: body.requestId, question: body.question };
}

/** 켜진 스위치로 접수 방식을 정한다 */
function acceptOptions() {
  const scenarios = getEnabledScenarios();
  return {
    slow: scenarios.includes('hintSlow'),
    noResponse: scenarios.includes('hintNoResponse'),
    pollsUntilDone: scenarios.includes('hintSlow')
      ? SLOW_POLLS_UNTIL_DONE
      : scenarios.includes('hintNoResponse')
        ? 1
        : null,
    failFirstAttempt: scenarios.includes('hintFail'),
  };
}

function failedResponse(code: string, message: string, requestId: string) {
  return mockErrorResponse(502, code, message, { requestId });
}

/** 접수한 요청을 스위치에 맞게 응답한다 (202·연결 끊김·지연 후 200·502) */
async function respondToAccepted(
  room: MockRoom,
  aiRequest: ReturnType<typeof acceptHintRequest>,
  options: ReturnType<typeof acceptOptions>,
  successMessage: string,
) {
  if (options.slow) {
    return ok(toAiRequestStatus(aiRequest), '요청을 처리하고 있습니다.', 202);
  }
  if (options.noResponse) {
    return HttpResponse.error();
  }
  await delay(MOCK_ANSWER_DELAY_MS);
  finishMockAiRequest(room, aiRequest);
  saveMockRoomDb();
  if (aiRequest.status === 'FAILED') {
    return failedResponse(
      aiRequest.error?.code ?? 'HINT_GENERATION_FAILED',
      aiRequest.error?.message ?? '',
      aiRequest.requestId,
    );
  }
  return ok(aiRequest.result, successMessage);
}

export const hintHandlers = [
  // 조회는 모든 세션 상태에서 가능하고 횟수를 차감하지 않는다
  http.get(`${API_BASE_URL}/api/rooms/:roomId/hints`, ({ request, params }) => {
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
    const data: HintListResponse = {
      hintCount: hintCountOf(room),
      hints: hintsFor(room),
      pendingRequests: pendingAiRequestsOf(room, { types: ['HINT', 'HINT_FOLLOWUP'] }),
    };
    return ok(data, '힌트 기록 조회 성공');
  }),

  http.post(`${API_BASE_URL}/api/rooms/:roomId/hints`, async ({ request, params }) => {
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

    const body = await parseBody(request);
    if ('error' in body) {
      return body.error;
    }
    const { requestId, question } = body;

    // 같은 requestId: 입력이 다르면 거부, 완료면 저장된 결과(연결 검사 없음), 처리 중이면 202, 실패면 다시 실행
    const existing = findMockAiRequest(room, requestId);
    if (existing) {
      if (existing.type !== 'HINT' || existing.input.question !== question) {
        return mockErrorResponse(
          409,
          'AI_REQUEST_ID_REUSED',
          '같은 요청 식별자로 다른 내용을 보낼 수 없습니다.',
        );
      }
      if (existing.status === 'SUCCEEDED') {
        return ok(existing.result, '힌트 요청 성공');
      }
      if (existing.status === 'PROCESSING') {
        return ok(toAiRequestStatus(existing), '요청을 처리하고 있습니다.', 202);
      }
    }

    const operableError = checkOperable(request, loaded);
    if (operableError) {
      return operableError;
    }
    if (question.includes(MOCK_BLOCKED_KEYWORD)) {
      // 접수 전에 차단하므로 기록·차감이 없다
      return mockErrorResponse(400, 'QUESTION_BLOCKED', BLOCKED_MESSAGE);
    }
    const processing = findProcessingHint(room);
    if (processing) {
      return mockErrorResponse(
        409,
        'AI_REQUEST_IN_PROGRESS',
        '이전 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.',
        { requestId: processing.requestId },
      );
    }
    if (hintCountOf(room).remaining <= 0) {
      return mockErrorResponse(409, 'HINT_LIMIT_EXCEEDED', '사용할 수 있는 힌트가 없습니다.');
    }

    const options = acceptOptions();
    const aiRequest = acceptHintRequest(room, {
      requestId,
      type: 'HINT',
      targetId: null,
      question,
      pollsUntilDone: options.pollsUntilDone,
      failFirstAttempt: options.failFirstAttempt,
    });
    saveMockRoomDb();
    return respondToAccepted(room, aiRequest, options, '힌트 요청 성공');
  }),

  http.post(
    `${API_BASE_URL}/api/rooms/:roomId/hints/:hintId/followup`,
    async ({ request, params }) => {
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
      const hintId = Number(params.hintId);

      const body = await parseBody(request);
      if ('error' in body) {
        return body.error;
      }
      const { requestId, question } = body;

      const existing = findMockAiRequest(room, requestId);
      if (existing) {
        if (
          existing.type !== 'HINT_FOLLOWUP' ||
          existing.targetId !== hintId ||
          existing.input.question !== question
        ) {
          return mockErrorResponse(
            409,
            'AI_REQUEST_ID_REUSED',
            '같은 요청 식별자로 다른 내용을 보낼 수 없습니다.',
          );
        }
        if (existing.status === 'SUCCEEDED') {
          return ok(existing.result, '보충 질문 성공');
        }
        if (existing.status === 'PROCESSING') {
          return ok(toAiRequestStatus(existing), '요청을 처리하고 있습니다.', 202);
        }
      }

      const operableError = checkOperable(request, loaded);
      if (operableError) {
        return operableError;
      }
      const hint = findMockHint(room, hintId);
      if (!hint) {
        return mockErrorResponse(404, 'HINT_NOT_FOUND', '힌트를 찾을 수 없습니다.');
      }
      if (hint.followup) {
        return mockErrorResponse(
          409,
          'HINT_FOLLOWUP_ALREADY_USED',
          '이 힌트의 보충 질문은 이미 사용했습니다.',
        );
      }
      if (question.includes(MOCK_BLOCKED_KEYWORD)) {
        return mockErrorResponse(400, 'QUESTION_BLOCKED', BLOCKED_MESSAGE);
      }
      const processing = findProcessingFollowup(room, hintId);
      if (processing) {
        return mockErrorResponse(
          409,
          'AI_REQUEST_IN_PROGRESS',
          '이전 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.',
          { requestId: processing.requestId },
        );
      }

      const options = acceptOptions();
      const aiRequest = acceptHintRequest(room, {
        requestId,
        type: 'HINT_FOLLOWUP',
        targetId: hintId,
        question,
        pollsUntilDone: options.pollsUntilDone,
        failFirstAttempt: options.failFirstAttempt,
      });
      saveMockRoomDb();
      return respondToAccepted(room, aiRequest, options, '보충 질문 성공');
    },
  ),
];
