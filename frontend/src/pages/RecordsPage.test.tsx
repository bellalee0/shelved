import { fireEvent, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { noteDraftKey } from '../features/note/draft';
import { acceptNpcAsk, finishMockAiRequest } from '../mocks/aiRequestDb';
import { acceptHintRequest } from '../mocks/hintDb';
import { server } from '../mocks/node';
import { seedMockRoom, type MockRoom } from '../mocks/roomDb';
import { loginMockUser, resetLoginState } from '../mocks/testing/loginMockUser';
import { recordApiRequests } from '../mocks/testing/recordRequests';
import { renderRoute } from '../mocks/testing/renderRoute';
import { seedAccusation } from '../mocks/testing/seedAccusation';

vi.mock('../game/PhaserGame', () => ({ PhaserGame: () => null }));

function ask(room: MockRoom, question: string, fail = false) {
  const request = acceptNpcAsk(room, {
    requestId: crypto.randomUUID(),
    suspectId: 1,
    question,
    pollsUntilDone: null,
    failFirstAttempt: fail,
  });
  finishMockAiRequest(room, request);
}

function askHint(room: MockRoom, type: 'HINT' | 'HINT_FOLLOWUP', question: string) {
  const request = acceptHintRequest(room, {
    requestId: crypto.randomUUID(),
    type,
    targetId: type === 'HINT' ? null : 1,
    question,
    pollsUntilDone: null,
    failFirstAttempt: false,
  });
  finishMockAiRequest(room, request);
}

function storeDraft(userId: number, roomId: number, content: string) {
  localStorage.setItem(
    noteDraftKey(userId, roomId),
    JSON.stringify({ content, baseRevision: 0, savedAt: Date.now() }),
  );
}

async function seedRecords() {
  const user = await loginMockUser();
  const room = seedMockRoom(user.userId, { caseId: 1, status: 'PAUSED' });
  ask(room, '어젯밤에 어디 계셨나요?');
  ask(room, '실패한 질문입니다', true);
  askHint(room, 'HINT', '무엇부터 확인할까요?');
  askHint(room, 'HINT_FOLLOWUP', '조금 더 알려 주세요');
  room.note = { content: '서버에 저장한 메모', revision: 1, updatedAtMs: Date.now() };
  storeDraft(user.userId, room.roomId, '아직 저장하지 못한 메모');
  return { user, room };
}

beforeEach(() => {
  resetLoginState();
});

afterEach(() => {
  server.events.removeAllListeners();
  vi.useRealTimers();
});

describe('수사 기록 화면', () => {
  it('대화 원문·힌트와 보충 질문·서버 노트·이 기기의 초안을 읽기 전용으로 보여 준다', async () => {
    // given
    const { room, user } = await seedRecords();

    // when
    renderRoute(`/rooms/${room.roomId}/records`);

    // then
    expect(await screen.findByText('어젯밤에 어디 계셨나요?')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: '해리스와의 대화' })).toBeInTheDocument();
    expect(await screen.findByText('질문: 무엇부터 확인할까요?')).toBeInTheDocument();
    expect(screen.getByText('보충 질문: 조금 더 알려 주세요')).toBeInTheDocument();
    expect(await screen.findByText('서버에 저장한 메모')).toBeInTheDocument();
    const draft = screen.getByRole('textbox', { name: '복사 가능한 미저장 노트' });
    expect(draft).toHaveValue('아직 저장하지 못한 메모');
    expect(draft).toHaveAttribute('readonly');
    // 실패한 요청은 다시 보여 주거나 재실행하지 않는다
    expect(screen.queryByText('실패한 질문입니다')).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: /다시 보내기|저장|채점/ })).not.toBeInTheDocument();
    // 초안은 지우지 않는다
    expect(localStorage.getItem(noteDraftKey(user.userId, room.roomId))).not.toBeNull();
  });

  it('조회 요청만 보내고 하트비트·일시정지·재개·AI·노트 저장·결과 조회는 보내지 않는다', async () => {
    // given 채점 실패 지목도 있는 일시정지 세션
    const { room } = await seedRecords();
    seedAccusation(room, {
      suspectId: 2,
      trickExplanation: '아직 확신이 없는 추측입니다.',
      outcome: 'error',
    });
    const requests = recordApiRequests();
    vi.useFakeTimers({ shouldAdvanceTime: true });

    // when 하트비트·폴링 간격보다 오래 둔다
    renderRoute(`/rooms/${room.roomId}/records`);
    await screen.findByText('어젯밤에 어디 계셨나요?');
    await screen.findByText('서버에 저장한 메모');
    await screen.findByText('질문: 무엇부터 확인할까요?');
    await vi.advanceTimersByTimeAsync(30_000);

    // then
    const base = `GET /api/rooms/${room.roomId}`;
    expect([...requests].sort()).toEqual(
      [
        `${base}/hints`,
        `${base}/map`,
        `${base}/notes/me`,
        `${base}/npc/1/messages`,
        `${base}/state`,
      ].sort(),
    );
  });

  it('기록이 없으면 "아직 기록이 없습니다"를 보여 주고 초안 영역은 없다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'CLEARED' });

    // when
    renderRoute(`/rooms/${room.roomId}/records`);

    // then 대화·힌트·노트
    await waitFor(() => expect(screen.getAllByText('아직 기록이 없습니다.')).toHaveLength(3));
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('서버 노트와 같은 초안은 미저장 초안으로 보여 주지 않는다', async () => {
    // given
    const { room, user } = await seedRecords();
    storeDraft(user.userId, room.roomId, '서버에 저장한 메모');

    // when
    renderRoute(`/rooms/${room.roomId}/records`);

    // then
    expect(await screen.findByText('서버에 저장한 메모')).toBeInTheDocument();
    expect(screen.queryByRole('textbox')).not.toBeInTheDocument();
  });

  it('"이전 화면으로"는 이동해 온 화면으로 돌아간다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'CLEARED' });
    const router = renderRoute(`/rooms/${room.roomId}/result`);
    fireEvent.click(await screen.findByRole('link', { name: '수사 기록 보기' }));

    // when
    fireEvent.click(await screen.findByRole('button', { name: '이전 화면으로' }));

    // then
    await waitFor(() =>
      expect(router.state.location.pathname).toBe(`/rooms/${room.roomId}/result`),
    );
  });

  it('주소로 직접 들어왔으면 "이전 화면으로"가 게임 화면으로 간다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'PAUSED' });
    const router = renderRoute(`/rooms/${room.roomId}/records`);
    // 메모리 라우터의 첫 항목은 key가 'default'라 직접 들어온 것과 같다
    expect(router.state.location.key).toBe('default');

    // when
    fireEvent.click(screen.getByRole('button', { name: '이전 화면으로' }));

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe(`/rooms/${room.roomId}`));
  });

  it('남의 세션이면 서버 문구를 보여 준다', async () => {
    // given
    await loginMockUser();

    // when
    renderRoute('/rooms/999/records');

    // then
    expect(await screen.findByText('세션이 존재하지 않습니다.')).toBeInTheDocument();
  });
});
