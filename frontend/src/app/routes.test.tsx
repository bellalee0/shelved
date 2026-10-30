import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { beforeEach, describe, expect, it } from 'vitest';
import type { UserMeResponse } from '../features/auth/types';
import { INITIAL_AUTH_STATE, useAuthStore } from '../features/auth/store';
import { routes } from './routes';

const PLAYER: UserMeResponse = {
  userId: 1,
  nickname: '왓슨',
  role: 'PLAYER',
  nicknameRequired: false,
};

beforeEach(() => {
  useAuthStore.setState({ ...INITIAL_AUTH_STATE, restoreStatus: 'ready' }, true);
});

function setLoggedInUser(user: UserMeResponse | null) {
  useAuthStore.setState({ accessToken: user ? 'access-token' : null, user });
}

/** 주어진 주소에서 시작하는 라우터를 그린다. 인증 초기화는 각 테스트가 저장소 상태로 직접 정한다 */
function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

describe('라우트 보호', () => {
  it('비로그인 사용자가 로그인 필요 화면에 들어가면 로그인 화면으로 보낸다', () => {
    // given
    setLoggedInUser(null);

    // when
    const router = renderAt('/admin?tab=cases');

    // then
    expect(router.state.location.pathname).toBe('/login');
    expect(router.state.location.state).toEqual({ from: '/admin?tab=cases' });
  });

  it('로그인하면 로그인 전에 가려던 화면으로 돌아간다', async () => {
    // given
    setLoggedInUser(null);
    const router = renderAt('/admin');

    // when
    act(() => setLoggedInUser({ ...PLAYER, role: 'ADMIN' }));

    // then
    expect(router.state.location.pathname).toBe('/admin');
    expect(await screen.findByRole('heading', { name: '관리자 화면' })).toBeInTheDocument();
  });

  it('닉네임이 없는 사용자는 다른 화면에서 닉네임 화면으로 보내고, 가려던 화면을 기억한다', () => {
    // given
    setLoggedInUser({ ...PLAYER, nickname: null, nicknameRequired: true });

    // when
    const router = renderAt('/');

    // then
    expect(router.state.location.pathname).toBe('/nickname');
    expect(router.state.location.state).toEqual({ from: '/' });
  });

  it('로그인 직후 닉네임이 없으면 로그인 화면에서 닉네임 화면으로 보낸다', () => {
    // given
    setLoggedInUser(null);
    const router = renderAt('/login');

    // when
    act(() => setLoggedInUser({ ...PLAYER, nickname: null, nicknameRequired: true }));

    // then
    expect(router.state.location.pathname).toBe('/nickname');
  });

  it('관리자가 아니면 관리자 화면에서 메인으로 보낸다', () => {
    // given
    setLoggedInUser(PLAYER);

    // when
    const router = renderAt('/admin');

    // then
    expect(router.state.location.pathname).toBe('/');
  });

  it('비로그인 사용자도 메인 화면을 볼 수 있고 로그인 링크가 보인다', () => {
    // given
    setLoggedInUser(null);

    // when
    const router = renderAt('/');

    // then
    expect(router.state.location.pathname).toBe('/');
    expect(screen.getByRole('link', { name: '로그인' })).toBeInTheDocument();
  });

  it('관리자로 로그인하면 메인 화면 사용자 메뉴에 닉네임·로그아웃·관리자 화면 링크가 보인다', () => {
    // given
    setLoggedInUser({ ...PLAYER, nickname: '관리자', role: 'ADMIN' });

    // when
    renderAt('/');

    // then
    const menu = screen.getByRole('navigation', { name: '사용자 메뉴' });
    expect(menu).toHaveTextContent('관리자');
    expect(screen.getByRole('link', { name: '관리자 화면' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '로그아웃' })).toBeInTheDocument();
  });
});
