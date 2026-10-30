import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { routes } from '../app/routes';
import { loginWithGoogle } from '../features/auth/api';
import { completeLogin, connectAuthToApiClient } from '../features/auth/session';
import { INITIAL_AUTH_STATE, useAuthStore } from '../features/auth/store';
import { createMockGoogleIdToken } from '../mocks/mockGoogleIdToken';
import { setAccessTokenGetter, setTokenRefresher } from '../shared/api/client';

beforeEach(async () => {
  useAuthStore.setState({ ...INITIAL_AUTH_STATE, restoreStatus: 'ready' }, true);
  connectAuthToApiClient();
  // 목 서버에 닉네임이 없는 새 사용자로 로그인한다
  await completeLogin(await loginWithGoogle({ idToken: createMockGoogleIdToken('new-player') }));
});

afterEach(() => {
  setAccessTokenGetter(null);
  setTokenRefresher(null);
});

function renderNicknamePage(from = '/') {
  const router = createMemoryRouter(routes, {
    initialEntries: [{ pathname: '/nickname', state: { from } }],
  });
  render(
    <QueryClientProvider client={new QueryClient()}>
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

function typeNickname(value: string) {
  fireEvent.change(screen.getByLabelText('닉네임'), { target: { value } });
}

describe('NicknamePage', () => {
  it('입력 중 규칙을 어기면 안내 문구를 보여준다', () => {
    // given
    renderNicknamePage();

    // when
    typeNickname('탐정!');

    // then
    expect(screen.getByLabelText('닉네임')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByText(/완성형 한글·영문·숫자만 사용할 수 있습니다/)).toBeInTheDocument();
  });

  it('서버가 형식 위반(400 INVALID_INPUT)으로 거절하면 응답 message를 그대로 보여준다', async () => {
    // given
    renderNicknamePage();
    typeNickname('ㄱㄴ');

    // when
    fireEvent.click(screen.getByRole('button', { name: '시작하기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다.',
    );
  });

  it('이미 사용 중인 닉네임(409 NICKNAME_EXIST)이면 응답 message를 그대로 보여준다', async () => {
    // given
    renderNicknamePage();
    // 목 데이터의 기존 사용자 닉네임 '셜록초보'와 같은 이름
    typeNickname('셜록초보');

    // when
    fireEvent.click(screen.getByRole('button', { name: '시작하기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent('이미 사용 중인 닉네임입니다.');
  });

  it('저장에 성공하면 사용자 정보를 갱신하고 가려던 화면으로 이동한다', async () => {
    // given
    const router = renderNicknamePage('/');
    typeNickname('새내기탐정');

    // when
    fireEvent.click(screen.getByRole('button', { name: '시작하기' }));

    // then
    expect(await screen.findByRole('navigation', { name: '사용자 메뉴' })).toHaveTextContent(
      '새내기탐정',
    );
    expect(router.state.location.pathname).toBe('/');
    expect(useAuthStore.getState().user?.nicknameRequired).toBe(false);
  });
});
