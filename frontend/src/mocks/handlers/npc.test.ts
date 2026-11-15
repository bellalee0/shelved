import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { askNpc, getNpcMessages } from '../../features/npc/api';
import { createRoom, getRoomState, startRoom } from '../../features/room/api';
import { getAiRequest } from '../../shared/api/aiRequests';
import { isApiError } from '../../shared/api/apiError';
import { resetApiSession, setConnectionId } from '../../shared/api/client';
import { setScenario } from '../scenarios';
import { loginMockUser, resetLoginState } from '../testing/loginMockUser';
import { MOCK_BLOCKED_KEYWORD } from './npc';

// NPC 질문 목이 명세의 requestId 규칙과 기록 일관성을 지키는지 확인한다 (가상 사건 suspectId 3: 톰, 4: 대화 불가)

const TOM = 3;
const NOT_CONVERSABLE = 4;
const REQUEST_ID = '5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11';
const OTHER_REQUEST_ID = '7a1d2c3b-4e5f-4a6b-8c7d-9e0f1a2b3c4d';
const QUESTION = '어젯밤 9시에 어디에 있었나요?';

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

beforeEach(async () => {
  resetLoginState();
  await loginMockUser();
  roomId = (await createRoom({ caseId: 1 })).roomId;
  setConnectionId((await startRoom(roomId)).connectionId);
});

afterEach(() => {
  resetApiSession();
});

describe('NPC 질문 목', () => {
  it('질문에 답하고 대화 기록과 세션 상태의 conversations에 저장한다', async () => {
    // when
    const result = await askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION });

    // then
    expect(result.kind).toBe('completed');
    const messages = await getNpcMessages(roomId, TOM);
    expect(messages.messages.map((message) => [message.senderType, message.sequenceNo])).toEqual([
      ['USER', 1],
      ['NPC', 2],
    ]);
    expect(messages.pendingRequests).toEqual([]);
    const state = await getRoomState(roomId);
    expect(state.conversations).toEqual([
      expect.objectContaining({ suspectId: TOM, messageCount: 2 }),
    ]);
  });

  it('완료된 requestId를 다시 보내면 저장된 결과를 돌려주고 중복 저장하지 않는다', async () => {
    // given
    const first = await askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION });

    // when
    const again = await askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION });

    // then
    expect(again).toEqual(first);
    expect((await getNpcMessages(roomId, TOM)).messages).toHaveLength(2);
  });

  it('같은 requestId에 다른 질문이면 409 AI_REQUEST_ID_REUSED다', async () => {
    // given
    await askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION });

    // when
    const error = await catchApiError(
      askNpc(roomId, TOM, { requestId: REQUEST_ID, question: '다른 질문' }),
    );

    // then
    expect(error).toMatchObject({ status: 409, code: 'AI_REQUEST_ID_REUSED' });
  });

  it('차단 문구가 들어간 질문은 400 QUESTION_BLOCKED이고 기록하지 않는다', async () => {
    // when
    const error = await catchApiError(
      askNpc(roomId, TOM, { requestId: REQUEST_ID, question: `${MOCK_BLOCKED_KEYWORD}이 누구죠?` }),
    );

    // then
    expect(error).toMatchObject({ status: 400, code: 'QUESTION_BLOCKED' });
    const messages = await getNpcMessages(roomId, TOM);
    expect(messages.messages).toEqual([]);
    expect(messages.pendingRequests).toEqual([]);
    // 접수되지 않았으므로 같은 requestId를 다른 질문에 쓸 수 있다
    await expect(
      askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION }),
    ).resolves.toMatchObject({ kind: 'completed' });
  });

  it('대화할 수 없는 인물이면 400 NPC_NOT_CONVERSABLE이다', async () => {
    // when
    const error = await catchApiError(
      askNpc(roomId, NOT_CONVERSABLE, { requestId: REQUEST_ID, question: QUESTION }),
    );

    // then
    expect(error).toMatchObject({ status: 400, code: 'NPC_NOT_CONVERSABLE' });
  });

  it('npcFail이면 첫 실행이 502이고, 같은 requestId로 다시 보내면 성공한다', async () => {
    // given
    setScenario('npcFail', true);

    // when
    const error = await catchApiError(
      askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION }),
    );

    // then
    expect(error).toMatchObject({
      status: 502,
      code: 'NPC_ANSWER_FAILED',
      detail: { requestId: REQUEST_ID },
    });
    expect((await getNpcMessages(roomId, TOM)).pendingRequests).toEqual([
      expect.objectContaining({ requestId: REQUEST_ID, status: 'FAILED' }),
    ]);
    expect((await getRoomState(roomId)).aiRequests).toHaveLength(1);

    // when
    const retried = await askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION });

    // then
    expect(retried.kind).toBe('completed');
    expect((await getNpcMessages(roomId, TOM)).pendingRequests).toEqual([]);
  });

  it('npcSlow면 202를 주고 상태 조회 두 번째에 완료하며, 그동안 같은 NPC의 다른 질문은 409다', async () => {
    // given
    setScenario('npcSlow', true);

    // when
    const result = await askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION });

    // then
    expect(result).toMatchObject({ kind: 'processing', data: { status: 'PROCESSING' } });
    const inProgress = await catchApiError(
      askNpc(roomId, TOM, { requestId: OTHER_REQUEST_ID, question: '다른 질문' }),
    );
    expect(inProgress).toMatchObject({
      status: 409,
      code: 'AI_REQUEST_IN_PROGRESS',
      detail: { requestId: REQUEST_ID },
    });
    // 같은 requestId 재전송은 202
    await expect(
      askNpc(roomId, TOM, { requestId: REQUEST_ID, question: QUESTION }),
    ).resolves.toMatchObject({ kind: 'processing' });

    // when
    const firstPoll = await getAiRequest(roomId, REQUEST_ID);
    const secondPoll = await getAiRequest(roomId, REQUEST_ID);

    // then
    expect(firstPoll.status).toBe('PROCESSING');
    expect(secondPoll).toMatchObject({ status: 'SUCCEEDED', result: { requestId: REQUEST_ID } });
    expect((await getNpcMessages(roomId, TOM)).messages).toHaveLength(2);
  });

  it('없는 requestId 상태 조회는 404 AI_REQUEST_NOT_FOUND다', async () => {
    // when
    const error = await catchApiError(getAiRequest(roomId, REQUEST_ID));

    // then
    expect(error).toMatchObject({ status: 404, code: 'AI_REQUEST_NOT_FOUND' });
  });
});
