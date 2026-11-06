import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../app/routes';
import { seedMockRoom } from '../mocks/roomDb';
import { server } from '../mocks/node';
import { setScenario } from '../mocks/scenarios';
import { loginMockUser, resetLoginState } from '../mocks/testing/loginMockUser';

// 수사 시작 후 이동하는 게임 화면의 Phaser는 jsdom에서 그릴 수 없으므로 빈 화면으로 바꾼다
vi.mock('../game/PhaserGame', () => ({ PhaserGame: () => null }));

/** 서버로 나간 API 요청("METHOD /path")을 기록한다 */
function recordRequests() {
  const requests: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    requests.push(`${request.method} ${url.pathname}`);
  });
  return requests;
}

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

beforeEach(() => {
  resetLoginState();
});

afterEach(() => {
  // 테스트마다 붙인 요청 기록 리스너를 걷어낸다
  server.events.removeAllListeners();
});

describe('메인 화면 · 비로그인', () => {
  it('사건 카드만 보이고 세션 목록은 없으며 인증이 필요한 API를 부르지 않는다', async () => {
    // given
    const requests = recordRequests();

    // when
    renderAt('/');

    // then
    expect(
      await screen.findByRole('heading', { name: '시계탑 저택의 마지막 종소리' }),
    ).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '안개 낀 등대의 빈 의자' })).toBeInTheDocument();
    expect(screen.getByText('제한시간 20분 · 힌트 5회 · 지목 3회')).toBeInTheDocument();
    expect(screen.getByText('쉬움')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '진행 중인 수사' })).not.toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: '지난 수사' })).not.toBeInTheDocument();
    expect(requests.filter((request) => request.includes('/api/rooms'))).toEqual([]);
    expect(screen.getAllByTestId('case-cover-fallback')).toHaveLength(2);
  });

  it('카드를 고르면 로그인 화면으로 가고, 고른 사건 주소를 돌아올 경로로 넘긴다', async () => {
    // given
    const router = renderAt('/');

    // when
    fireEvent.click(
      await screen.findByRole('button', { name: '안개 낀 등대의 빈 의자 수사 시작' }),
    );

    // then
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.state).toEqual({ from: '/?case=2' });
  });

  it('로그인하면 고른 사건이 선택된 메인 화면으로 돌아와 시작 확인 창을 보여주고, 자동으로 세션을 만들지 않는다', async () => {
    // given
    const requests = recordRequests();
    const router = renderAt('/');
    fireEvent.click(
      await screen.findByRole('button', { name: '안개 낀 등대의 빈 의자 수사 시작' }),
    );

    // when
    await act(async () => {
      await loginMockUser();
    });

    // then
    const dialog = await screen.findByRole('dialog', { name: '수사를 시작할까요?' });
    expect(within(dialog).getByText('안개 낀 등대의 빈 의자')).toBeInTheDocument();
    expect(router.state.location.pathname + router.state.location.search).toBe('/?case=2');
    expect(requests).not.toContain('POST /api/rooms');
  });
});

describe('메인 화면 · 로그인', () => {
  it('진행 중인 수사와 지난 수사를 보여주고, 진행 중인 사건 카드에 배지를 붙인다', async () => {
    // given
    const player = await loginMockUser();
    const active = seedMockRoom(player.userId, {
      caseId: 1,
      status: 'PAUSED',
      remainingMs: 768_750,
    });
    const ended = seedMockRoom(player.userId, { caseId: 2, status: 'CLEARED', durationSec: 912 });

    // when
    renderAt('/');

    // then
    const activeSection = await screen.findByRole('region', { name: '진행 중인 수사' });
    expect(await within(activeSection).findByText('남은 12:48')).toBeInTheDocument();
    expect(within(activeSection).getByText('일시정지')).toBeInTheDocument();
    expect(
      within(activeSection).getByRole('link', { name: '시계탑 저택의 마지막 종소리 이어하기' }),
    ).toHaveAttribute('href', `/rooms/${active.roomId}`);

    const endedSection = await screen.findByRole('region', { name: '지난 수사' });
    expect(await within(endedSection).findByText('해결')).toBeInTheDocument();
    expect(within(endedSection).getByText('15:12 걸림')).toBeInTheDocument();
    expect(within(endedSection).getByText(/^\d\d\/\d\d \d\d:\d\d$/)).toBeInTheDocument();
    expect(
      within(endedSection).getByRole('link', { name: '안개 낀 등대의 빈 의자 결과 보기' }),
    ).toHaveAttribute('href', `/rooms/${ended.roomId}/result`);

    const clocktowerCard = screen.getByRole('heading', {
      name: '시계탑 저택의 마지막 종소리',
      level: 3,
    });
    const lighthouseCard = screen.getByRole('heading', {
      name: '안개 낀 등대의 빈 의자',
      level: 3,
    });
    expect(within(clocktowerCard.closest('article')!).getByText('진행 중')).toBeInTheDocument();
    expect(
      within(lighthouseCard.closest('article')!).queryByText('진행 중'),
    ).not.toBeInTheDocument();
  });

  it('채점 실패는 "다시 채점하기", 채점 중은 "채점 대기"로 보여준다', async () => {
    // given
    const player = await loginMockUser();
    seedMockRoom(player.userId, {
      caseId: 1,
      status: 'PAUSED',
      remainingMs: 0,
      pendingGradingStatus: 'ERROR',
    });
    seedMockRoom(player.userId, {
      caseId: 2,
      status: 'PAUSED',
      remainingMs: 0,
      pendingGradingStatus: 'PENDING',
    });

    // when
    renderAt('/');

    // then
    expect(
      await screen.findByRole('link', { name: '시계탑 저택의 마지막 종소리 다시 채점하기' }),
    ).toBeInTheDocument();
    expect(screen.getByText('채점 실패')).toBeInTheDocument();
    expect(screen.getByText('채점 대기')).toBeInTheDocument();
    expect(screen.getAllByText('남은 00:00')).toHaveLength(2);
  });

  it('세션이 없으면 각 목록의 빈 상태 문구를 보여준다', async () => {
    // given
    await loginMockUser();

    // when
    renderAt('/');

    // then
    expect(
      await screen.findByText('진행 중인 수사가 없습니다. 사건을 골라 시작해 보세요.'),
    ).toBeInTheDocument();
    expect(await screen.findByText('아직 완료한 수사가 없습니다.')).toBeInTheDocument();
  });

  it('세션 목록 조회에 실패하면 목록마다 오류와 다시 시도를 보여주고, 사건 카드는 계속 보인다', async () => {
    // given
    await loginMockUser();
    setScenario('roomsError', true);

    // when
    renderAt('/');

    // then
    const alerts = await screen.findAllByRole('alert');
    expect(alerts).toHaveLength(2);
    expect(alerts[0]).toHaveTextContent('서버 내부 오류가 발생했습니다.');
    expect(
      screen.getByRole('heading', { name: '시계탑 저택의 마지막 종소리' }),
    ).toBeInTheDocument();
  });
});

describe('메인 화면 · 사건 목록 조회 실패', () => {
  it('실패하면 오류와 다시 시도를 보여주고, 다시 시도하면 카드가 나온다', async () => {
    // given
    setScenario('casesError', true);
    renderAt('/');
    const alert = await screen.findByRole('alert');
    expect(alert).toHaveTextContent('서버 내부 오류가 발생했습니다.');

    // when
    setScenario('casesError', false);
    fireEvent.click(within(alert).getByRole('button', { name: '다시 시도' }));

    // then
    expect(
      await screen.findByRole('heading', { name: '시계탑 저택의 마지막 종소리' }),
    ).toBeInTheDocument();
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
  });

  it('네트워크 오류면 공통 안내 문구를 보여준다', async () => {
    // given
    setScenario('networkError', true);

    // when
    renderAt('/');

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent('서버에 연결할 수 없습니다.');
  });
});

describe('메인 화면 · 수사 시작', () => {
  it('확인 창에서 수사 시작을 누르면 세션을 만들고 게임 화면으로 이동한다', async () => {
    // given
    await loginMockUser();
    const router = renderAt('/?case=1');
    const dialog = await screen.findByRole('dialog', { name: '수사를 시작할까요?' });

    // when
    fireEvent.click(within(dialog).getByRole('button', { name: '수사 시작' }));

    // then
    expect(await screen.findByRole('heading', { name: '수사 준비' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/rooms/1');
  });

  it('시작 버튼을 연달아 눌러도 세션 생성 요청은 한 번만 나간다', async () => {
    // given
    await loginMockUser();
    renderAt('/?case=1');
    const requests = recordRequests();
    const dialog = await screen.findByRole('dialog', { name: '수사를 시작할까요?' });
    const startButton = within(dialog).getByRole('button', { name: '수사 시작' });

    // when
    // 같은 틱에 연달아 두 번 누른다
    fireEvent.click(startButton);
    fireEvent.click(startButton);
    await screen.findByRole('heading', { name: '수사 준비' });

    // then
    expect(requests.filter((request) => request === 'POST /api/rooms')).toHaveLength(1);
  });

  it('진행 중인 수사가 있으면 안내 창을 보여주고, 이어하기는 기존 세션의 게임 화면으로 이동한다', async () => {
    // given
    const player = await loginMockUser();
    const existing = seedMockRoom(player.userId, { caseId: 1, status: 'PAUSED' });
    const router = renderAt('/?case=1');
    const confirmDialog = await screen.findByRole('dialog', { name: '수사를 시작할까요?' });

    // when
    fireEvent.click(within(confirmDialog).getByRole('button', { name: '수사 시작' }));
    const conflictDialog = await screen.findByRole('dialog', { name: '진행 중인 수사가 있습니다' });
    fireEvent.click(within(conflictDialog).getByRole('button', { name: '이어하기' }));

    // then
    expect(router.state.location.pathname).toBe(`/rooms/${existing.roomId}`);
  });

  it('취소하면 선택이 풀려 확인 창이 닫힌다', async () => {
    // given
    await loginMockUser();
    const router = renderAt('/?case=1');
    const dialog = await screen.findByRole('dialog', { name: '수사를 시작할까요?' });

    // when
    fireEvent.click(within(dialog).getByRole('button', { name: '취소' }));

    // then
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('시작 요청이 실패하면 확인 창 안에 백엔드 메시지를 보여준다', async () => {
    // given
    await loginMockUser();
    renderAt('/?case=1');
    const dialog = await screen.findByRole('dialog', { name: '수사를 시작할까요?' });
    setScenario('networkError', true);

    // when
    fireEvent.click(within(dialog).getByRole('button', { name: '수사 시작' }));

    // then
    expect(await within(dialog).findByRole('alert')).toHaveTextContent(
      '서버에 연결할 수 없습니다.',
    );
  });
});
