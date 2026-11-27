import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { http } from 'msw';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { RoomResultResponse } from '../features/room/types';
import { findMockReveal } from '../mocks/data/reveals';
import { mockErrorResponse, ok } from '../mocks/handlers/auth';
import { server } from '../mocks/node';
import { findMockRoom, seedMockRoom, type MockRoom } from '../mocks/roomDb';
import { loginMockUser, resetLoginState } from '../mocks/testing/loginMockUser';
import { recordApiRequests } from '../mocks/testing/recordRequests';
import { renderRoute } from '../mocks/testing/renderRoute';
import { seedAccusation } from '../mocks/testing/seedAccusation';

// 다시 도전 후 이동하는 게임 화면의 Phaser는 jsdom에서 그릴 수 없으므로 빈 화면으로 바꾼다
vi.mock('../game/PhaserGame', () => ({ PhaserGame: () => null }));

const CORRECT = '녹음한 종소리로 시계의 시각을 착각하게 했습니다.';
const WRONG = '아직 확신이 없는 추측입니다.';

async function seedCleared() {
  const user = await loginMockUser();
  const room = seedMockRoom(user.userId, { caseId: 1, status: 'IN_PROGRESS' });
  seedAccusation(room, { suspectId: 1, trickExplanation: WRONG });
  seedAccusation(room, { suspectId: 2, trickExplanation: CORRECT });
  return { user, room };
}

/** 결과 응답의 일부만 바꿔 돌려준다 */
function overrideResult(room: MockRoom, override: Partial<RoomResultResponse>) {
  const base: RoomResultResponse = {
    roomId: room.roomId,
    caseId: room.caseId,
    displayTitle: '시계탑 저택의 마지막 종소리',
    isTest: false,
    status: 'EXPIRED',
    endedAt: '2026-10-04T20:11:00',
    durationSec: 1200,
    hintUsedCount: 0,
    accusationCompletedCount: 0,
    ...findMockReveal(1)!,
    accusations: [],
    ...override,
  };
  server.use(http.get('*/api/rooms/:roomId/result', () => ok(base, '결과 조회 성공')));
}

beforeEach(() => {
  resetLoginState();
});

afterEach(() => {
  server.events.removeAllListeners();
});

describe('결과 화면', () => {
  it('해결한 수사의 머리말·원작·범인·트릭·해설·플레이 기록·지목 기록을 보여 준다', async () => {
    // given
    const { room } = await seedCleared();

    // when
    renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByRole('heading', { name: '사건을 해결했습니다' })).toBeInTheDocument();
    expect(screen.getByText('2번째 지목에서 범인과 트릭을 모두 맞혔습니다.')).toBeInTheDocument();
    expect(screen.getByText('해결')).toBeInTheDocument();
    expect(screen.getByText('(가상) 마지막 종')).toBeInTheDocument();
    expect(screen.getByText('(가상) 홍작가')).toBeInTheDocument();
    expect(screen.getByText(/화면 확인을 위해 지어낸 원작 소개/)).toBeInTheDocument();
    expect(
      screen.getByText('미리 녹음한 종소리로 시계의 시각을 착각하게 했다'),
    ).toBeInTheDocument();
    expect(screen.getByText(/종소리를 미리 녹음해 두었습니다/)).toBeInTheDocument();
    for (const label of ['걸린 시간', '힌트 사용', '지목', '종료 시각']) {
      expect(screen.getByText(label, { selector: 'dt' })).toBeInTheDocument();
    }
    expect(screen.getByText('2회', { selector: 'dd' })).toBeInTheDocument();
    const records = screen.getByRole('list', { name: '내 지목 기록' });
    expect(records).toHaveTextContent('1번째 지목');
    expect(records).toHaveTextContent('오답');
    expect(records).toHaveTextContent('2번째 지목');
    expect(records).toHaveTextContent('정답');
    expect(
      within(records).queryByRole('button', { name: '같은 답안 다시 채점' }),
    ).not.toBeInTheDocument();
  });

  it.each([
    ['FAILED', '지목 기회를 모두 사용했습니다', '실패'],
    ['EXPIRED', '제한시간이 끝났습니다', '시간 만료'],
  ] as const)('%s 상태의 머리말을 보여 준다', async (status, title, label) => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status });

    // when
    renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByRole('heading', { name: title })).toBeInTheDocument();
    expect(screen.getByText(label)).toBeInTheDocument();
    expect(screen.getByText('제출한 지목이 없습니다.')).toBeInTheDocument();
  });

  it('범인의 스프라이트를 찾지 못하면 이름 첫 글자의 기본 실루엣을 보여 준다', async () => {
    // given 목 manifest에는 스프라이트가 없다
    const { room } = await seedCleared();

    // when
    renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByTestId('suspect-face-fallback')).toHaveTextContent('마');
  });

  it('원작·해설의 HTML은 해석하지 않고 글자와 줄바꿈 그대로 보여 준다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'EXPIRED' });
    overrideResult(room, {
      trickSummary: '<img src=x alt=injected>',
      revealExplanation: '첫 줄\n<b>둘째 줄</b>',
    });

    // when
    renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByText('<img src=x alt=injected>')).toBeInTheDocument();
    expect(screen.queryByRole('img', { name: 'injected' })).not.toBeInTheDocument();
    const explanation = screen.getByText(/둘째 줄/);
    expect(explanation.textContent).toBe('첫 줄\n<b>둘째 줄</b>');
    expect(explanation).toHaveClass('whitespace-pre-wrap');
  });

  it('종료 전이면(409) 결과를 그리지 않고 게임 화면으로 보내며 안내를 보여 준다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'PAUSED' });
    const requests = recordApiRequests();

    // when
    const router = renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByText('아직 종료되지 않은 수사입니다.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/rooms/${room.roomId}`);
    expect(await screen.findByText('수사를 이어서 진행합니다')).toBeInTheDocument();
    expect(requests.some((request) => request.includes('/api/rankings'))).toBe(false);
  });

  it('그 밖의 오류는 서버 문구와 다시 시도를 보여 준다', async () => {
    // given
    await loginMockUser();

    // when
    renderRoute('/rooms/999/result');

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent('세션이 존재하지 않습니다.');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });

  it('랭킹 영역은 상위 10건과 내 기록을 보여 주고 전체 랭킹으로 간다', async () => {
    // given
    const { room } = await seedCleared();
    const requests = recordApiRequests();
    const router = renderRoute(`/rooms/${room.roomId}/result`);

    // when
    const table = await screen.findByRole('table');

    // then
    expect(requests).toContain('GET /api/rankings/1?page=0&size=10');
    expect(within(table).getAllByRole('row')).toHaveLength(11);
    expect(screen.getByRole('group', { name: '내 최고 기록' })).toHaveTextContent('1위');

    // when
    fireEvent.click(screen.getByRole('link', { name: '전체 랭킹 보기' }));

    // then
    expect(router.state.location.pathname).toBe('/cases/1/ranking');
  });

  it('랭킹 조회가 실패해도 리빌은 보인다', async () => {
    // given
    const { room } = await seedCleared();
    server.use(
      http.get('*/api/rankings/:caseId', () =>
        mockErrorResponse(500, 'INTERNAL_SERVER_ERROR', '서버 내부 오류가 발생했습니다.'),
      ),
    );

    // when
    renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByText('서버 내부 오류가 발생했습니다.')).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '사건을 해결했습니다' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다시 도전' })).toBeInTheDocument();
  });

  it('관리자 테스트 세션은 랭킹·다시 도전을 숨기고 랭킹을 조회하지 않는다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'EXPIRED' });
    overrideResult(room, { isTest: true });
    const requests = recordApiRequests();

    // when
    renderRoute(`/rooms/${room.roomId}/result`);

    // then
    expect(await screen.findByText('관리자 테스트 · 랭킹 미반영')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '랭킹' })).not.toBeInTheDocument();
    expect(screen.queryByRole('button', { name: '다시 도전' })).not.toBeInTheDocument();
    expect(requests.some((request) => request.includes('/api/rankings'))).toBe(false);
  });

  it('다시 도전하면 새 세션을 만들고 새 게임 화면으로 간다', async () => {
    // given
    const { room, user } = await seedCleared();
    const router = renderRoute(`/rooms/${room.roomId}/result`);

    // when
    fireEvent.click(await screen.findByRole('button', { name: '다시 도전' }));

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe(`/rooms/${room.roomId + 1}`));
    expect(findMockRoom(room.roomId + 1, user.userId)?.caseId).toBe(1);
  });

  it('같은 사건의 진행 중인 수사가 있으면 이어하기를 안내한다', async () => {
    // given
    const { room, user } = await seedCleared();
    const ongoing = seedMockRoom(user.userId, { caseId: 1, status: 'PAUSED' });
    const router = renderRoute(`/rooms/${room.roomId}/result`);

    // when
    fireEvent.click(await screen.findByRole('button', { name: '다시 도전' }));
    const dialog = await screen.findByRole('dialog', { name: '진행 중인 수사가 있습니다' });
    fireEvent.click(within(dialog).getByRole('button', { name: '이어하기' }));

    // then
    expect(router.state.location.pathname).toBe(`/rooms/${ongoing.roomId}`);
  });

  it('수사 기록 보기로 읽기 전용 화면에 간다', async () => {
    // given
    const { room } = await seedCleared();
    const router = renderRoute(`/rooms/${room.roomId}/result`);

    // when
    fireEvent.click(await screen.findByRole('link', { name: '수사 기록 보기' }));

    // then
    expect(router.state.location.pathname).toBe(`/rooms/${room.roomId}/records`);
  });
});
