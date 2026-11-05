import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import {
  createRoom,
  getRooms,
  getRoomState,
  heartbeat,
  pauseRoom,
  resumeRoom,
  startRoom,
} from '../../features/room/api';
import { inspectEvidence } from '../../features/evidence/api';
import { getMyNote, updateMyNote } from '../../features/note/api';
import { isApiError } from '../../shared/api/apiError';
import { resetApiSession, setConnectionId } from '../../shared/api/client';
import { seedMockRoom, setMockClock } from '../roomDb';
import { loginMockUser, MOCK_ADMIN_GOOGLE_SUB, resetLoginState } from '../testing/loginMockUser';

const CLOCKTOWER_CASE_ID = 1;
const START_TIME_MS = new Date('2026-10-05T14:00:00').getTime();

let nowMs = START_TIME_MS;

/** 시계를 주어진 만큼 앞으로 보낸다 */
function advance(ms: number) {
  nowMs += ms;
}

/** 실패해야 하는 호출의 ApiError를 꺼낸다 */
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

/** 세션을 만들고 수사를 시작해 connectionId를 메모리에 보관한다 */
async function createAndStartRoom() {
  const { roomId } = await createRoom({ caseId: CLOCKTOWER_CASE_ID });
  const connection = await startRoom(roomId);
  setConnectionId(connection.connectionId);
  return { roomId, connectionId: connection.connectionId };
}

beforeEach(async () => {
  nowMs = START_TIME_MS;
  setMockClock(() => nowMs);
  resetLoginState();
  await loginMockUser();
});

afterEach(() => {
  resetApiSession();
});

describe('세션 생성', () => {
  it('새 세션은 READY이고 빈 노트(revision 0)가 함께 만들어진다', async () => {
    // given & when
    const created = await createRoom({ caseId: CLOCKTOWER_CASE_ID });
    const note = await getMyNote(created.roomId);

    // then
    expect(created.status).toBe('READY');
    expect(note).toMatchObject({ content: '', revision: 0, editable: false });
  });

  it('같은 사건의 미종료 세션이 있으면 409 ROOM_ALREADY_EXISTS와 기존 roomId를 돌려준다', async () => {
    // given
    const first = await createRoom({ caseId: CLOCKTOWER_CASE_ID });

    // when
    const error = await catchApiError(createRoom({ caseId: CLOCKTOWER_CASE_ID }));

    // then
    expect(error.status).toBe(409);
    expect(error.code).toBe('ROOM_ALREADY_EXISTS');
    expect(error.detail).toEqual({ roomId: first.roomId });
  });

  it('다른 사건이면 미종료 세션이 있어도 새로 만든다', async () => {
    // given
    await createRoom({ caseId: 1 });

    // when
    const second = await createRoom({ caseId: 2 });

    // then
    expect(second.caseId).toBe(2);
  });

  it('없는 사건이면 404 CASE_NOT_FOUND다', async () => {
    // when
    const error = await catchApiError(createRoom({ caseId: 999 }));

    // then
    expect(error.code).toBe('CASE_NOT_FOUND');
  });
});

describe('시작·하트비트·일시정지·이어하기', () => {
  it('start는 READY에서만 되고 connectionId를 발급하며, 다시 부르면 409 ROOM_NOT_READY다', async () => {
    // given
    const { roomId, connectionId } = await createAndStartRoom();

    // when
    const error = await catchApiError(startRoom(roomId));

    // then
    expect(connectionId).toBeTruthy();
    expect(error.code).toBe('ROOM_NOT_READY');
  });

  it('heartbeat는 헤더가 없으면 400 CONNECTION_ID_REQUIRED다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    setConnectionId(null);

    // when
    const error = await catchApiError(heartbeat(roomId));

    // then
    expect(error.status).toBe(400);
    expect(error.code).toBe('CONNECTION_ID_REQUIRED');
  });

  it('heartbeat는 현재 연결이 아니면 409 CONNECTION_REPLACED다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    setConnectionId('other-connection');

    // when
    const error = await catchApiError(heartbeat(roomId));

    // then
    expect(error.status).toBe(409);
    expect(error.code).toBe('CONNECTION_REPLACED');
  });

  it('pause는 헤더 없이 부를 수 있고 중복 호출해도 안전하다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    setConnectionId(null);
    advance(10_000);

    // when
    const first = await pauseRoom(roomId);
    advance(60_000);
    const second = await pauseRoom(roomId);

    // then
    expect(first.status).toBe('PAUSED');
    expect(first.elapsedActiveMs).toBe(10_000);
    expect(second).toMatchObject({ status: 'PAUSED', elapsedActiveMs: 10_000 });
  });

  it('PAUSED가 아닌 세션을 resume하면 409 ROOM_NOT_PAUSED다', async () => {
    // given
    const { roomId } = await createAndStartRoom();

    // when
    const error = await catchApiError(resumeRoom(roomId));

    // then
    expect(error.code).toBe('ROOM_NOT_PAUSED');
  });

  it('resume은 새 connectionId를 발급하고 이전 연결의 하트비트는 409가 된다', async () => {
    // given
    const { roomId, connectionId: oldConnectionId } = await createAndStartRoom();
    await pauseRoom(roomId);

    // when
    const resumed = await resumeRoom(roomId);
    setConnectionId(oldConnectionId);
    const error = await catchApiError(heartbeat(roomId));

    // then
    expect(resumed.connectionId).not.toBe(oldConnectionId);
    expect(error.code).toBe('CONNECTION_REPLACED');
  });

  it('마지막 접속 확인 후 15초가 지나면 그 시각 기준으로 PAUSED가 된다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    advance(5_000);
    await heartbeat(roomId);

    // when
    advance(16_000);
    const state = await getRoomState(roomId);

    // then
    expect(state.timer.status).toBe('PAUSED');
    expect(state.timer.elapsedActiveMs).toBe(5_000);
  });

  it('접속 확인이 15초 안에 이어지면 IN_PROGRESS를 유지하고 시간이 흐른다', async () => {
    // given
    const { roomId } = await createAndStartRoom();

    // when
    advance(14_000);
    const state = await getRoomState(roomId);

    // then
    expect(state.timer).toMatchObject({ status: 'IN_PROGRESS', elapsedActiveMs: 14_000 });
    expect(state.timer.remainingMs).toBe(1_200_000 - 14_000);
  });

  it('제한시간이 모두 지나면 EXPIRED가 되고 resume은 거부된다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    for (let elapsed = 0; elapsed < 1_200_000; elapsed += 5_000) {
      advance(5_000);
      await heartbeat(roomId).catch(() => undefined);
    }
    advance(5_000);

    // when
    const state = await getRoomState(roomId);
    const error = await catchApiError(resumeRoom(roomId));

    // then
    expect(state.timer).toMatchObject({ status: 'EXPIRED', remainingMs: 0 });
    expect(error.code).toBe('ROOM_NOT_PAUSED');
  });

  it('PAUSED 세션의 남은 시간이 0이면 resume은 409 ROOM_TIME_EXPIRED다', async () => {
    // given: 시간이 소진된 채 PAUSED인 세션은 목 시드로 만든다
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, {
      caseId: 2,
      status: 'PAUSED',
      remainingMs: 0,
      pendingGradingStatus: 'PENDING',
    });

    // when
    const error = await catchApiError(resumeRoom(room.roomId));

    // then
    expect(error.code).toBe('ROOM_TIME_EXPIRED');
  });
});

describe('물증 조사', () => {
  it('진행 중이면 물증 설명을 돌려주고, 없는 물증은 404 EVIDENCE_NOT_FOUND다', async () => {
    // given
    const { roomId } = await createAndStartRoom();

    // when
    const inspected = await inspectEvidence(roomId, 1);
    const error = await catchApiError(inspectEvidence(roomId, 999));

    // then
    expect(inspected).toMatchObject({ evidenceId: 1, name: '서재의 작은 상자' });
    expect(error.code).toBe('EVIDENCE_NOT_FOUND');
  });

  it('PAUSED일 때는 409 ROOM_NOT_IN_PROGRESS로 거부한다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    await pauseRoom(roomId);

    // when
    const error = await catchApiError(inspectEvidence(roomId, 1));

    // then
    expect(error.status).toBe(409);
    expect(error.code).toBe('ROOM_NOT_IN_PROGRESS');
  });

  it('연결 헤더가 없으면 400, 다른 연결이면 409 CONNECTION_REPLACED다', async () => {
    // given
    const { roomId } = await createAndStartRoom();

    // when
    setConnectionId(null);
    const missing = await catchApiError(inspectEvidence(roomId, 1));
    setConnectionId('other-connection');
    const replaced = await catchApiError(inspectEvidence(roomId, 1));

    // then
    expect(missing.code).toBe('CONNECTION_ID_REQUIRED');
    expect(replaced.code).toBe('CONNECTION_REPLACED');
  });
});

describe('개인 노트', () => {
  it('저장하면 revision이 1 오르고 내용이 원문 그대로 보존된다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    const content = '  첫 줄\n\n둘째 줄  ';

    // when
    const saved = await updateMyNote(roomId, { content, revision: 0 });
    const fetched = await getMyNote(roomId);

    // then
    expect(saved).toMatchObject({ content, revision: 1, editable: true, maxLength: 5000 });
    expect(fetched.content).toBe(content);
  });

  it('revision이 다르면 409 NOTE_REVISION_CONFLICT와 서버 내용을 detail로 돌려준다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    await updateMyNote(roomId, { content: '서버에 저장된 내용', revision: 0 });

    // when
    const error = await catchApiError(updateMyNote(roomId, { content: '내 내용', revision: 0 }));

    // then
    expect(error.status).toBe(409);
    expect(error.code).toBe('NOTE_REVISION_CONFLICT');
    expect(error.detail).toEqual({ currentRevision: 1, currentContent: '서버에 저장된 내용' });
  });

  it('최대 글자 수를 넘기면 400 INVALID_INPUT이다', async () => {
    // given
    const { roomId } = await createAndStartRoom();

    // when
    const error = await catchApiError(
      updateMyNote(roomId, { content: 'a'.repeat(5001), revision: 0 }),
    );

    // then
    expect(error.status).toBe(400);
    expect(error.code).toBe('INVALID_INPUT');
  });

  it('PAUSED일 때 저장은 거부되지만 조회는 되고 editable이 false다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    await pauseRoom(roomId);

    // when
    const error = await catchApiError(updateMyNote(roomId, { content: '내용', revision: 0 }));
    const note = await getMyNote(roomId);

    // then
    expect(error.code).toBe('ROOM_NOT_IN_PROGRESS');
    expect(note.editable).toBe(false);
  });
});

describe('세션 소유자 확인과 목록', () => {
  it('다른 사용자의 세션은 모든 세션 API에서 404 ROOM_NOT_FOUND다', async () => {
    // given
    const { roomId } = await createAndStartRoom();
    await loginMockUser(MOCK_ADMIN_GOOGLE_SUB);

    // when
    const results = await Promise.all([
      catchApiError(getRoomState(roomId)),
      catchApiError(pauseRoom(roomId)),
      catchApiError(getMyNote(roomId)),
    ]);

    // then
    expect(results.map((error) => [error.status, error.code])).toEqual([
      [404, 'ROOM_NOT_FOUND'],
      [404, 'ROOM_NOT_FOUND'],
      [404, 'ROOM_NOT_FOUND'],
    ]);
  });

  it('로그인하지 않고 세션 API를 부르면 401 LOGIN_REQUIRED다', async () => {
    // given
    resetLoginState();

    // when
    const error = await catchApiError(getRooms());

    // then
    expect(error.status).toBe(401);
    expect(error.code).toBe('LOGIN_REQUIRED');
  });

  it('목록은 status로 진행 중·종료를 나누고 최신 생성 순이다', async () => {
    // given
    const user = await loginMockUser();
    seedMockRoom(user.userId, { caseId: 1, status: 'CLEARED', durationSec: 912 });
    advance(1_000);
    await createRoom({ caseId: 2 });

    // when
    const active = await getRooms({ status: 'ACTIVE' });
    const ended = await getRooms({ status: 'ENDED' });

    // then
    expect(active.content.map((room) => room.caseId)).toEqual([2]);
    expect(ended.content).toHaveLength(1);
    expect(ended.content[0]).toMatchObject({ status: 'CLEARED', durationSec: 912, remainingMs: 0 });
  });
});
