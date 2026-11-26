import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createAccusation, getAccusations } from '../../features/accusation/api';
import type { AccusationCreateRequest } from '../../features/accusation/types';
import { createRoom, getRoomState, pauseRoom, startRoom } from '../../features/room/api';
import { getAiRequest } from '../../shared/api/aiRequests';
import { isApiError } from '../../shared/api/apiError';
import { resetApiSession, setConnectionId } from '../../shared/api/client';
import { MOCK_GRADING_RETRY_INTERVAL_MS } from '../accusationDb';
import { currentTimeMs, findMockRoom, setMockClock } from '../roomDb';
import { setScenario } from '../scenarios';
import { loginMockUser, resetLoginState } from '../testing/loginMockUser';

// 지목 목이 명세의 한도·재채점 간격·대체·requestId 규칙과 기록 일관성을 지키는지 확인한다.
// 목의 판정 규칙(가상 사건): 사건 1의 범인은 suspectId 2, 키워드 "종소리"·"시계" 둘 다 → 정답, 하나 → 부분 정답.

const ID = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const CORRECT = '종소리가 울리기 전에 시계를 미리 돌려 두었다.';
const PARTIAL = '시계를 미리 돌려 두어 시각을 속였다.';
const WRONG = '창문으로 몰래 드나들어 알리바이를 만들었다.';

let roomId = 0;
let userId = 0;

function body(n: number, suspectId: number, trickExplanation: string): AccusationCreateRequest {
  return { requestId: ID(n), suspectId, trickExplanation };
}

const NEW = { isRetry: false };
const RETRY = { isRetry: true };

async function catchApiError(promise: Promise<unknown>) {
  try {
    await promise;
  } catch (error) {
    if (isApiError(error)) {
      return error;
    }
    throw error;
  }
  throw new Error('실패해야 하는 호출이 성공했습니다.');
}

/** 목 시계를 앞당긴다 (재채점 간격 확인용) */
function advanceClock(ms: number) {
  const base = currentTimeMs();
  const started = Date.now();
  setMockClock(() => base + ms + (Date.now() - started));
}

/** 남은 시간을 1초로 줄이고 2초 지나게 해 마감시킨다 (하트비트 이탈 판정 15초보다 짧게) */
function exhaustTime() {
  const room = findMockRoom(roomId, userId)!;
  room.elapsedActiveMs = 20 * 60 * 1000 - 1000;
  room.segmentStartedAtMs = currentTimeMs();
  room.lastSeenAtMs = currentTimeMs();
  advanceClock(2000);
}

beforeEach(async () => {
  resetLoginState();
  userId = (await loginMockUser()).userId;
  roomId = (await createRoom({ caseId: 1 })).roomId;
  setConnectionId((await startRoom(roomId)).connectionId);
});

afterEach(() => {
  resetApiSession();
  setMockClock(null);
});

describe('지목 목 판정과 종료', () => {
  it('부분 정답 → 정답이면 CLEARED가 되고 횟수·기록·세션 상태가 서로 맞는다', async () => {
    // when
    const partial = await createAccusation(roomId, body(1, 2, PARTIAL), NEW);
    const correct = await createAccusation(roomId, body(2, 2, CORRECT), NEW);

    // then
    expect(partial).toMatchObject({
      kind: 'completed',
      data: {
        accusation: { verdict: 'PARTIAL', attemptNo: 1, gradingStatus: 'COMPLETED' },
        roomStatus: 'IN_PROGRESS',
      },
    });
    expect(correct).toMatchObject({
      kind: 'completed',
      data: { accusation: { verdict: 'CORRECT', attemptNo: 2 }, roomStatus: 'CLEARED' },
    });
    const list = await getAccusations(roomId);
    expect(list.accusations.map((item) => item.verdict)).toEqual(['PARTIAL', 'CORRECT']);
    expect(list.accusationCount).toEqual({ limit: 3, used: 2, reserved: 0, remaining: 1 });
    const state = await getRoomState(roomId);
    expect(state.timer.status).toBe('CLEARED');
    expect(state.accusationCount).toEqual(list.accusationCount);
    expect(state.pendingAccusation).toBeNull();
  });

  it('범인이 틀리면 오답이고, 3번째 완료도 정답이 아니면 FAILED다', async () => {
    // when
    await createAccusation(roomId, body(1, 1, CORRECT), NEW);
    await createAccusation(roomId, body(2, 2, WRONG), NEW);
    const third = await createAccusation(roomId, body(3, 2, PARTIAL), NEW);

    // then
    const list = await getAccusations(roomId);
    expect(list.accusations.map((item) => item.verdict)).toEqual([
      'INCORRECT',
      'INCORRECT',
      'PARTIAL',
    ]);
    expect(third).toMatchObject({ data: { roomStatus: 'FAILED' } });
    expect((await getRoomState(roomId)).timer.status).toBe('FAILED');
  });

  it('피드백에는 문단 구분 줄바꿈이 들어 있을 수 있고 항목별 판정은 주지 않는다', async () => {
    // when
    const result = await createAccusation(roomId, body(1, 2, PARTIAL), NEW);

    // then
    expect(result.kind === 'completed' && result.data.accusation.feedback).toContain('\n\n');
    expect(JSON.stringify(result)).not.toContain('element');
  });
});

describe('지목 목 requestId 규칙', () => {
  it('같은 requestId 재전송은 저장된 결과를 주고 중복 저장·차감하지 않는다 (연결 헤더 없이도)', async () => {
    // given
    const first = await createAccusation(roomId, body(1, 2, PARTIAL), NEW);

    // when
    const again = await createAccusation(roomId, body(1, 2, PARTIAL), RETRY);

    // then
    expect(again).toEqual(first);
    const list = await getAccusations(roomId);
    expect(list.accusations).toHaveLength(1);
    expect(list.accusationCount.used).toBe(1);
  });

  it('같은 requestId에 다른 답안이면 409 AI_REQUEST_ID_REUSED다', async () => {
    await createAccusation(roomId, body(1, 2, PARTIAL), NEW);

    const error = await catchApiError(createAccusation(roomId, body(1, 2, WRONG), NEW));

    expect(error.code).toBe('AI_REQUEST_ID_REUSED');
  });

  it('채점 중 새 지목은 409 AI_REQUEST_IN_PROGRESS이고, 202 뒤 상태 조회 두 번째에 완료된다', async () => {
    // given
    setScenario('accusationSlow', true);
    const accepted = await createAccusation(roomId, body(1, 2, PARTIAL), NEW);
    expect(accepted.kind).toBe('processing');
    const state = await getRoomState(roomId);
    expect(state.accusationCount).toMatchObject({ reserved: 1, remaining: 2 });
    expect(state.pendingAccusation).toMatchObject({ requestId: ID(1), gradingStatus: 'PENDING' });
    expect(state.aiRequests).toMatchObject([{ requestId: ID(1), type: 'ACCUSATION' }]);

    // when
    const conflict = await catchApiError(createAccusation(roomId, body(2, 2, WRONG), NEW));

    // then
    expect(conflict.code).toBe('AI_REQUEST_IN_PROGRESS');
    expect(conflict.detail).toEqual({ requestId: ID(1) });
    expect((await getAiRequest(roomId, ID(1))).status).toBe('PROCESSING');
    const done = await getAiRequest(roomId, ID(1));
    expect(done.status).toBe('SUCCEEDED');
    expect(done.result).toMatchObject({ accusation: { verdict: 'PARTIAL' } });
    expect((await getRoomState(roomId)).aiRequests).toEqual([]);
  });

  it('세션이 진행 중인데 기회가 없으면 409 ACCUSATION_LIMIT_EXCEEDED다', async () => {
    // given 정상 흐름에서는 3번째 완료로 세션이 끝나므로, 목 DB에 완료된 지목 3건을 직접 넣는다 (다른 화면과 겹친 경우)
    const room = findMockRoom(roomId, userId)!;
    room.accusations = [1, 2, 3].map((n) => ({
      accusationId: n,
      requestId: ID(100 + n),
      suspectId: 1,
      trickExplanation: WRONG,
      submittedAtMs: currentTimeMs(),
      gradingStatus: 'COMPLETED' as const,
      verdict: 'INCORRECT' as const,
      feedback: '피드백',
      attemptNo: n,
      retryAvailableAtMs: null,
      superseded: false,
    }));

    // when
    const error = await catchApiError(createAccusation(roomId, body(1, 2, CORRECT), NEW));

    // then
    expect(error.code).toBe('ACCUSATION_LIMIT_EXCEEDED');
    expect((await getAccusations(roomId)).accusations).toHaveLength(3);
  });

  it('3번 모두 정답이 아니면 세션이 끝나 새 답안은 진행 중 검사에서 막힌다', async () => {
    await createAccusation(roomId, body(1, 1, WRONG), NEW);
    await createAccusation(roomId, body(2, 1, WRONG), NEW);
    await createAccusation(roomId, body(3, 1, WRONG), NEW);

    const error = await catchApiError(createAccusation(roomId, body(4, 1, WRONG), NEW));

    expect(error.code).toBe('ROOM_NOT_IN_PROGRESS');
    expect((await getAccusations(roomId)).accusationCount.remaining).toBe(0);
  });
});

describe('지목 목 채점 실패와 재채점', () => {
  it('502 뒤 10초 전 재채점은 429, 10초 뒤 재채점은 성공하며 기회는 1회만 차감된다', async () => {
    // given
    setScenario('accusationFail', true);
    const failed = await catchApiError(createAccusation(roomId, body(1, 2, PARTIAL), NEW));
    expect(failed.status).toBe(502);
    expect(failed.code).toBe('GRADING_FAILED');
    expect(failed.detail).toMatchObject({ requestId: ID(1), accusationId: 1 });
    const afterFail = await getRoomState(roomId);
    expect(afterFail.accusationCount).toEqual({ limit: 3, used: 0, reserved: 0, remaining: 3 });
    expect(afterFail.pendingAccusation).toMatchObject({ gradingStatus: 'ERROR' });
    expect(afterFail.aiRequests[0]).toMatchObject({
      status: 'FAILED',
      retryAvailableAt: expect.any(String),
    });

    // when 10초 전
    const tooSoon = await catchApiError(createAccusation(roomId, body(1, 2, PARTIAL), RETRY));

    // then
    expect(tooSoon.status).toBe(429);
    expect(tooSoon.code).toBe('GRADING_RETRY_TOO_SOON');
    expect(tooSoon.detail).toEqual({ retryAvailableAt: afterFail.aiRequests[0].retryAvailableAt });

    // when 10초 뒤 (연결 헤더 없이)
    advanceClock(MOCK_GRADING_RETRY_INTERVAL_MS);
    setConnectionId(null);
    const retried = await createAccusation(roomId, body(1, 2, PARTIAL), RETRY);

    // then
    expect(retried).toMatchObject({ kind: 'completed', data: { accusation: { attemptNo: 1 } } });
    const list = await getAccusations(roomId);
    expect(list.accusations).toHaveLength(1);
    expect(list.accusationCount.used).toBe(1);
  });

  it('일시정지 상태에서도 연결 헤더 없이 재채점할 수 있다', async () => {
    // given
    setScenario('accusationFail', true);
    await catchApiError(createAccusation(roomId, body(1, 2, PARTIAL), NEW));
    await pauseRoom(roomId);
    setConnectionId(null);
    advanceClock(MOCK_GRADING_RETRY_INTERVAL_MS);

    // when
    const retried = await createAccusation(roomId, body(1, 2, PARTIAL), RETRY);

    // then
    expect(retried.kind).toBe('completed');
  });

  it('실패 후 새 답안을 내면 이전 실패는 대체되고, 그 requestId 재채점은 409 AI_REQUEST_SUPERSEDED다', async () => {
    // given
    setScenario('accusationFail', true);
    await catchApiError(createAccusation(roomId, body(1, 2, PARTIAL), NEW));
    setScenario('accusationFail', false);

    // when
    await createAccusation(roomId, body(2, 2, WRONG), NEW);
    advanceClock(MOCK_GRADING_RETRY_INTERVAL_MS);
    const error = await catchApiError(createAccusation(roomId, body(1, 2, PARTIAL), RETRY));

    // then
    expect(error.code).toBe('AI_REQUEST_SUPERSEDED');
    const list = await getAccusations(roomId);
    expect(list.accusations.map((item) => [item.gradingStatus, item.superseded])).toEqual([
      ['ERROR', true],
      ['COMPLETED', false],
    ]);
    const state = await getRoomState(roomId);
    expect(state.pendingAccusation).toBeNull();
    expect(state.aiRequests).toEqual([]);
  });
});

describe('지목 목 마감', () => {
  it('마감 후 새 답안은 409 ROOM_TIME_EXPIRED다', async () => {
    // given
    exhaustTime();

    // when
    const error = await catchApiError(createAccusation(roomId, body(1, 2, PARTIAL), NEW));

    // then
    expect(error.code).toBe('ROOM_TIME_EXPIRED');
  });

  it('채점 실패한 지목이 있으면 마감 후에도 세션이 채점 대기로 남고 재채점 결과가 적용된다', async () => {
    // given
    setScenario('accusationFail', true);
    await catchApiError(createAccusation(roomId, body(1, 2, CORRECT), NEW));
    exhaustTime();
    advanceClock(MOCK_GRADING_RETRY_INTERVAL_MS);
    const waiting = await getRoomState(roomId);
    expect(waiting.timer).toMatchObject({ remainingMs: 0, awaitingGrading: true });

    // when
    setConnectionId(null);
    const retried = await createAccusation(roomId, body(1, 2, CORRECT), RETRY);

    // then
    expect(retried).toMatchObject({ data: { roomStatus: 'CLEARED' } });
  });
});
