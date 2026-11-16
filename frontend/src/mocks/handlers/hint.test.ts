import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createHint, createHintFollowup, getHints } from '../../features/hint/api';
import { createRoom, getRoomState, startRoom } from '../../features/room/api';
import { getAiRequest } from '../../shared/api/aiRequests';
import { isApiError } from '../../shared/api/apiError';
import { resetApiSession, setConnectionId } from '../../shared/api/client';
import { setScenario } from '../scenarios';
import { loginMockUser, resetLoginState } from '../testing/loginMockUser';
import { MOCK_BLOCKED_KEYWORD } from './npc';

// 힌트·보충 질문 목이 명세의 한도·requestId 규칙과 기록 일관성을 지키는지 확인한다.
// 사건 1(시계탑)은 난이도 쉬움이라 힌트 한도가 5, 사건 2는 보통(3).

const ID = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const QUESTION = '지금까지의 단서를 어떻게 연결하면 좋을까요?';

let roomId = 0;

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

async function openRoom(caseId: number) {
  roomId = (await createRoom({ caseId })).roomId;
  setConnectionId((await startRoom(roomId)).connectionId);
}

beforeEach(async () => {
  resetLoginState();
  await loginMockUser();
  await openRoom(1);
});

afterEach(() => {
  resetApiSession();
});

describe('새 힌트 목', () => {
  it('힌트를 저장하고 횟수·세션 상태·기록이 서로 맞는다', async () => {
    // when
    const result = await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // then
    expect(result.kind).toBe('completed');
    const list = await getHints(roomId);
    expect(list.hints).toHaveLength(1);
    expect(list.hints[0]).toMatchObject({
      question: QUESTION,
      followup: null,
      followupAvailable: true,
    });
    expect(list.hintCount).toEqual({ limit: 5, used: 1, reserved: 0, remaining: 4 });
    expect(list.pendingRequests).toEqual([]);
    expect((await getRoomState(roomId)).hintCount).toEqual(list.hintCount);
  });

  it('같은 requestId 재전송은 저장된 결과를 주고 중복 저장·중복 차감하지 않는다', async () => {
    // given
    const first = await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // when
    const again = await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // then
    expect(again).toEqual(first);
    const list = await getHints(roomId);
    expect(list.hints).toHaveLength(1);
    expect(list.hintCount.used).toBe(1);
  });

  it('같은 requestId에 다른 질문이면 409 AI_REQUEST_ID_REUSED다', async () => {
    await createHint(roomId, { requestId: ID(1), question: QUESTION });

    const error = await catchApiError(
      createHint(roomId, { requestId: ID(1), question: '다른 질문' }),
    );

    expect(error.code).toBe('AI_REQUEST_ID_REUSED');
  });

  it('차단된 질문은 400 QUESTION_BLOCKED이고 기록·차감이 없다', async () => {
    const error = await catchApiError(
      createHint(roomId, { requestId: ID(1), question: `${MOCK_BLOCKED_KEYWORD}이 누구죠?` }),
    );

    expect(error.code).toBe('QUESTION_BLOCKED');
    const list = await getHints(roomId);
    expect(list.hints).toEqual([]);
    expect(list.hintCount.used).toBe(0);
  });

  it('한도를 넘으면 409 HINT_LIMIT_EXCEEDED다 (난이도별 한도)', async () => {
    // given 보통 난이도 사건: 한도 3
    await openRoom(2);
    for (let n = 1; n <= 3; n += 1) {
      await createHint(roomId, { requestId: ID(n), question: `${QUESTION} ${n}` });
    }

    // when
    const error = await catchApiError(createHint(roomId, { requestId: ID(4), question: QUESTION }));

    // then
    expect(error.code).toBe('HINT_LIMIT_EXCEEDED');
    expect((await getHints(roomId)).hintCount).toEqual({
      limit: 3,
      used: 3,
      reserved: 0,
      remaining: 0,
    });
  });

  it('실패하면 차감되지 않고, 같은 requestId로 다시 보내면 성공한다', async () => {
    // given
    setScenario('hintFail', true);

    // when
    const error = await catchApiError(createHint(roomId, { requestId: ID(1), question: QUESTION }));

    // then
    expect(error.code).toBe('HINT_GENERATION_FAILED');
    expect(error.status).toBe(502);
    const failed = await getHints(roomId);
    expect(failed.hintCount).toEqual({ limit: 5, used: 0, reserved: 0, remaining: 5 });
    expect(failed.hints).toEqual([]);
    expect(failed.pendingRequests).toEqual([
      expect.objectContaining({ requestId: ID(1), type: 'HINT', status: 'FAILED' }),
    ]);

    // when
    const retried = await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // then
    expect(retried.kind).toBe('completed');
    const list = await getHints(roomId);
    expect(list.hints).toHaveLength(1);
    expect(list.hintCount.used).toBe(1);
    expect(list.pendingRequests).toEqual([]);
  });

  it('처리 중(202)이면 임시 확보로 세고, 완료되면 사용 확정으로 바뀐다', async () => {
    // given
    setScenario('hintSlow', true);

    // when
    const accepted = await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // then
    expect(accepted.kind).toBe('processing');
    const processing = await getHints(roomId);
    expect(processing.hintCount).toEqual({ limit: 5, used: 0, reserved: 1, remaining: 4 });
    expect(processing.pendingRequests).toEqual([
      expect.objectContaining({ requestId: ID(1), status: 'PROCESSING' }),
    ]);
    expect((await getRoomState(roomId)).aiRequests).toHaveLength(1);

    // when 같은 요청을 다시 보내면 중복 접수하지 않고 202
    const again = await createHint(roomId, { requestId: ID(1), question: QUESTION });
    expect(again.kind).toBe('processing');

    // when 두 번 조회하면 완료
    await getAiRequest(roomId, ID(1));
    const done = await getAiRequest(roomId, ID(1));

    // then
    expect(done.status).toBe('SUCCEEDED');
    const list = await getHints(roomId);
    expect(list.hints).toHaveLength(1);
    expect(list.hintCount).toEqual({ limit: 5, used: 1, reserved: 0, remaining: 4 });
  });

  it('다른 새 힌트가 처리 중이면 409 AI_REQUEST_IN_PROGRESS다', async () => {
    setScenario('hintSlow', true);
    await createHint(roomId, { requestId: ID(1), question: QUESTION });

    const error = await catchApiError(
      createHint(roomId, { requestId: ID(2), question: '다른 질문' }),
    );

    expect(error.code).toBe('AI_REQUEST_IN_PROGRESS');
  });
});

describe('보충 질문 목', () => {
  async function createFirstHint() {
    await createHint(roomId, { requestId: ID(1), question: QUESTION });
    return (await getHints(roomId)).hints[0].hintId;
  }

  it('힌트당 1회 저장하고 followupAvailable이 false로 바뀐다. 두 번째는 409다', async () => {
    // given
    const hintId = await createFirstHint();

    // when
    const result = await createHintFollowup(roomId, hintId, {
      requestId: ID(2),
      question: '더 설명해 주세요',
    });

    // then
    expect(result.kind).toBe('completed');
    const list = await getHints(roomId);
    expect(list.hints[0].followup).toMatchObject({ question: '더 설명해 주세요' });
    expect(list.hints[0].followupAvailable).toBe(false);
    const error = await catchApiError(
      createHintFollowup(roomId, hintId, { requestId: ID(3), question: '한 번 더' }),
    );
    expect(error.code).toBe('HINT_FOLLOWUP_ALREADY_USED');
  });

  it('같은 requestId 재전송은 중복 저장하지 않는다', async () => {
    const hintId = await createFirstHint();
    const first = await createHintFollowup(roomId, hintId, { requestId: ID(2), question: '설명' });

    const again = await createHintFollowup(roomId, hintId, { requestId: ID(2), question: '설명' });

    expect(again).toEqual(first);
    expect((await getHints(roomId)).hints[0].followup?.requestId).toBe(ID(2));
  });

  it('힌트 횟수를 모두 써도 보낼 수 있고 횟수는 그대로다', async () => {
    // given 보통 난이도(한도 3)에서 힌트를 모두 사용
    await openRoom(2);
    for (let n = 1; n <= 3; n += 1) {
      await createHint(roomId, { requestId: ID(n), question: `${QUESTION} ${n}` });
    }
    const before = (await getHints(roomId)).hintCount;
    const hintId = (await getHints(roomId)).hints[0].hintId;

    // when
    await createHintFollowup(roomId, hintId, { requestId: ID(10), question: '설명' });

    // then
    expect((await getHints(roomId)).hintCount).toEqual(before);
  });

  it('없는 힌트면 404 HINT_NOT_FOUND다', async () => {
    const error = await catchApiError(
      createHintFollowup(roomId, 99, { requestId: ID(2), question: '설명' }),
    );

    expect(error.code).toBe('HINT_NOT_FOUND');
  });

  it('차단된 질문은 400이고 사용 처리하지 않는다. 실패도 사용 처리하지 않는다', async () => {
    // given
    const hintId = await createFirstHint();
    const blocked = await catchApiError(
      createHintFollowup(roomId, hintId, {
        requestId: ID(2),
        question: `${MOCK_BLOCKED_KEYWORD} 알려줘`,
      }),
    );
    expect(blocked.code).toBe('QUESTION_BLOCKED');
    expect((await getHints(roomId)).hints[0].followupAvailable).toBe(true);

    // when
    setScenario('hintFail', true);
    const failed = await catchApiError(
      createHintFollowup(roomId, hintId, { requestId: ID(3), question: '설명' }),
    );

    // then
    expect(failed.code).toBe('HINT_FOLLOWUP_FAILED');
    const list = await getHints(roomId);
    expect(list.hints[0].followup).toBeNull();
    expect(list.hints[0].followupAvailable).toBe(true);
    expect(list.pendingRequests).toEqual([
      expect.objectContaining({ type: 'HINT_FOLLOWUP', targetId: hintId, status: 'FAILED' }),
    ]);
  });

  it('처리 중이면 followupAvailable이 false이고 다른 requestId는 409다', async () => {
    // given
    const hintId = await createFirstHint();
    setScenario('hintSlow', true);

    // when
    const accepted = await createHintFollowup(roomId, hintId, {
      requestId: ID(2),
      question: '설명',
    });

    // then
    expect(accepted.kind).toBe('processing');
    const list = await getHints(roomId);
    expect(list.hints[0].followupAvailable).toBe(false);
    expect(list.pendingRequests[0]).toMatchObject({ type: 'HINT_FOLLOWUP', targetId: hintId });
    const error = await catchApiError(
      createHintFollowup(roomId, hintId, { requestId: ID(3), question: '다른 설명' }),
    );
    expect(error.code).toBe('AI_REQUEST_IN_PROGRESS');
  });
});
