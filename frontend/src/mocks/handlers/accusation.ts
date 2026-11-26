import { delay, http } from 'msw';
import type {
  AccusationCreateRequest,
  AccusationListResponse,
} from '../../features/accusation/types';
import {
  TRICK_EXPLANATION_MAX_LENGTH,
  TRICK_EXPLANATION_MIN_LENGTH,
} from '../../shared/constants/accusation';
import {
  acceptAccusation,
  accusationCountOf,
  accusationsFor,
  findMockAccusationByRequestId,
  findProcessingAccusation,
  gradingFailedDetail,
  isMockSuspect,
} from '../accusationDb';
import { findMockAiRequest, finishMockAiRequest, toAiRequestStatus } from '../aiRequestDb';
import { checkOperable, loadOwnedRoom } from '../roomAccess';
import {
  currentTimeMs,
  isEnded,
  isOutOfTime,
  saveMockRoomDb,
  toServerTime,
  type MockAccusationRequest,
  type MockRoom,
} from '../roomDb';
import { checkScenario, getEnabledScenarios } from '../scenarios';
import { mockErrorResponse, ok } from './auth';

// docs/api/hint-accusation.md "지목"의 기록 조회·제출(새 답안·재채점). requestId 규칙은 docs/api/README.md "AI 요청 처리".
// 판정 규칙은 mocks/accusationDb.ts 맨 위 주석에 있다 (가상 사건 전용).
//
// 개발 중 재현 방법 (스위치는 window.shelvedMock.setScenario로 켠다)
// - 기본: 약 1초 뒤 200 채점 결과
// - accusationSlow: 첫 POST가 202(채점 중), 상태 조회 두 번째에 완료
// - accusationFail: 첫 실행은 502 GRADING_FAILED(기회 미차감). 10초 뒤 같은 답안으로 재채점하면 성공
//   (10초 전에는 429 GRADING_RETRY_TOO_SOON. accusationSlow와 함께 켜면 조회 결과가 FAILED)
// - 실패 후 다른 답안을 내면 이전 실패는 superseded, 그 requestId로 재채점하면 409 AI_REQUEST_SUPERSEDED
// - 채점 중 새 지목 409 AI_REQUEST_IN_PROGRESS, 같은 requestId에 다른 답안 409 AI_REQUEST_ID_REUSED,
//   완료된 requestId 재전송은 저장된 결과 200 (중복 저장·차감 없음)
// - 기회 소진 409 ACCUSATION_LIMIT_EXCEEDED, 마감 후 새 답안 409 ROOM_TIME_EXPIRED
// - 마감 시간을 당기려면 window.shelvedMock.shortenTime(roomId, ms)

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/** 채점 결과까지의 지연(ms). 테스트에서는 기다리지 않는다 */
const MOCK_GRADING_DELAY_MS = import.meta.env.MODE === 'test' ? 0 : 1000;
/** accusationSlow: 완료까지 필요한 상태 조회 횟수 */
const SLOW_POLLS_UNTIL_DONE = 2;

const UUID_PATTERN = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

type ParsedBody = AccusationCreateRequest | { error: Response };

async function parseBody(request: Request): Promise<ParsedBody> {
  const body = (await request.json().catch(() => ({}))) as Partial<AccusationCreateRequest>;
  if (typeof body.requestId !== 'string' || !UUID_PATTERN.test(body.requestId)) {
    return {
      error: mockErrorResponse(400, 'INVALID_INPUT', '요청 식별자 형식이 올바르지 않습니다.', {
        field: 'requestId',
      }),
    };
  }
  if (typeof body.suspectId !== 'number') {
    return {
      error: mockErrorResponse(400, 'INVALID_INPUT', '지목할 인물을 선택해 주세요.', {
        field: 'suspectId',
      }),
    };
  }
  const length = typeof body.trickExplanation === 'string' ? body.trickExplanation.length : 0;
  if (length < TRICK_EXPLANATION_MIN_LENGTH || length > TRICK_EXPLANATION_MAX_LENGTH) {
    return {
      error: mockErrorResponse(
        400,
        'INVALID_INPUT',
        `트릭 설명은 ${TRICK_EXPLANATION_MIN_LENGTH}~${TRICK_EXPLANATION_MAX_LENGTH}자로 입력해 주세요.`,
        { field: 'trickExplanation' },
      ),
    };
  }
  return {
    requestId: body.requestId,
    suspectId: body.suspectId,
    trickExplanation: body.trickExplanation!,
  };
}

function acceptOptions() {
  const scenarios = getEnabledScenarios();
  const slow = scenarios.includes('accusationSlow');
  return {
    slow,
    pollsUntilDone: slow ? SLOW_POLLS_UNTIL_DONE : null,
    failFirstAttempt: scenarios.includes('accusationFail'),
  };
}

/** 접수한 채점을 스위치에 맞게 응답한다 (202·지연 후 200·502) */
async function respondToAccepted(
  room: MockRoom,
  aiRequest: MockAccusationRequest,
  options: ReturnType<typeof acceptOptions>,
) {
  if (options.slow) {
    return ok(toAiRequestStatus(aiRequest), '요청을 처리하고 있습니다.', 202);
  }
  await delay(MOCK_GRADING_DELAY_MS);
  finishMockAiRequest(room, aiRequest);
  saveMockRoomDb();
  if (aiRequest.status === 'FAILED') {
    return mockErrorResponse(
      502,
      'GRADING_FAILED',
      aiRequest.error?.message ?? '',
      gradingFailedDetail(room, aiRequest),
    );
  }
  return ok(aiRequest.result, '지목 채점 성공');
}

export const accusationHandlers = [
  // 조회는 모든 세션 상태에서 가능하다
  http.get(`${API_BASE_URL}/api/rooms/:roomId/accusations`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    const data: AccusationListResponse = {
      accusationCount: accusationCountOf(loaded.room),
      accusations: accusationsFor(loaded.room),
    };
    return ok(data, '지목 기록 조회 성공');
  }),

  http.post(`${API_BASE_URL}/api/rooms/:roomId/accusations`, async ({ request, params }) => {
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
    const { requestId, suspectId, trickExplanation } = body;

    // 같은 requestId: 입력이 다르면 거부, 완료면 저장된 결과(연결 검사 없음), 처리 중이면 202, 실패면 재채점
    const existing = findMockAiRequest(room, requestId);
    if (existing) {
      if (
        existing.type !== 'ACCUSATION' ||
        existing.input.suspectId !== suspectId ||
        existing.input.trickExplanation !== trickExplanation
      ) {
        return mockErrorResponse(
          409,
          'AI_REQUEST_ID_REUSED',
          '같은 요청 식별자로 다른 내용을 보낼 수 없습니다.',
        );
      }
      if (existing.status === 'SUCCEEDED') {
        return ok(existing.result, '지목 채점 성공');
      }
      if (existing.status === 'PROCESSING') {
        return ok(toAiRequestStatus(existing), '요청을 처리하고 있습니다.', 202);
      }
      // 재채점: 연결 헤더 불필요, 종료 전(IN_PROGRESS·PAUSED, 마감 후 포함), 대체되지 않음, 최소 간격 경과
      if (findMockAccusationByRequestId(room, requestId)?.superseded) {
        return mockErrorResponse(
          409,
          'AI_REQUEST_SUPERSEDED',
          '새 답안으로 대체된 제출은 다시 채점할 수 없습니다.',
        );
      }
      if (isEnded(room.status)) {
        return mockErrorResponse(409, 'ROOM_NOT_IN_PROGRESS', '진행 중인 수사가 아닙니다.');
      }
      if (existing.retryAvailableAtMs != null && currentTimeMs() < existing.retryAvailableAtMs) {
        return mockErrorResponse(
          429,
          'GRADING_RETRY_TOO_SOON',
          '잠시 후 다시 채점할 수 있습니다.',
          { retryAvailableAt: toServerTime(existing.retryAvailableAtMs) },
        );
      }
      const options = acceptOptions();
      const retried = acceptAccusation(room, {
        requestId,
        suspectId,
        trickExplanation,
        pollsUntilDone: options.pollsUntilDone,
        failFirstAttempt: options.failFirstAttempt,
      });
      saveMockRoomDb();
      return respondToAccepted(room, retried, options);
    }

    // 새 답안: 연결 헤더 필수, 진행 중·남은 시간·남은 기회·채점 중 없음
    const operableError = checkOperable(request, loaded);
    if (operableError) {
      return operableError;
    }
    if (isOutOfTime(room)) {
      return mockErrorResponse(409, 'ROOM_TIME_EXPIRED', '제한시간이 끝났습니다.');
    }
    if (!isMockSuspect(room, suspectId)) {
      return mockErrorResponse(404, 'SUSPECT_NOT_FOUND', '등장인물이 존재하지 않습니다.');
    }
    const processing = findProcessingAccusation(room);
    if (processing) {
      return mockErrorResponse(
        409,
        'AI_REQUEST_IN_PROGRESS',
        '이전 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요.',
        { requestId: processing.requestId },
      );
    }
    if (accusationCountOf(room).remaining <= 0) {
      return mockErrorResponse(409, 'ACCUSATION_LIMIT_EXCEEDED', '남은 지목 기회가 없습니다.');
    }

    const options = acceptOptions();
    const aiRequest = acceptAccusation(room, {
      requestId,
      suspectId,
      trickExplanation,
      pollsUntilDone: options.pollsUntilDone,
      failFirstAttempt: options.failFirstAttempt,
    });
    saveMockRoomDb();
    return respondToAccepted(room, aiRequest, options);
  }),
];
