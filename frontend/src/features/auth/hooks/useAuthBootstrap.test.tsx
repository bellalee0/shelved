import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { createMockGoogleIdToken } from '../../../mocks/mockGoogleIdToken';
import { setAccessTokenGetter, setTokenRefresher } from '../../../shared/api/client';
import { loginWithGoogle } from '../api';
import { completeLogin, logoutSession } from '../session';
import { INITIAL_AUTH_STATE, useAuthStore } from '../store';
import { useAuthBootstrap } from './useAuthBootstrap';

beforeEach(() => {
  useAuthStore.setState(INITIAL_AUTH_STATE, true);
});

afterEach(() => {
  setAccessTokenGetter(null);
  setTokenRefresher(null);
});

function renderBootstrap(queryClient: QueryClient) {
  function wrapper({ children }: { children: ReactNode }) {
    return <QueryClientProvider client={queryClient}>{children}</QueryClientProvider>;
  }
  return renderHook(() => useAuthBootstrap(), { wrapper });
}

describe('useAuthBootstrap', () => {
  it('저장된 토큰이 없으면 복원을 끝내고 ready 상태가 된다', async () => {
    // given
    const queryClient = new QueryClient();

    // when
    const { result } = renderBootstrap(queryClient);

    // then
    await waitFor(() => expect(result.current.restoreStatus).toBe('ready'));
  });

  it('로그아웃하면 이전 사용자의 Query 캐시를 모두 비운다', async () => {
    // given
    const queryClient = new QueryClient();
    const { result } = renderBootstrap(queryClient);
    await waitFor(() => expect(result.current.restoreStatus).toBe('ready'));
    await act(async () => {
      await completeLogin(
        await loginWithGoogle({ idToken: createMockGoogleIdToken('cache-user') }),
      );
    });
    queryClient.setQueryData(['rooms', 'me'], [{ roomId: 1 }]);

    // when
    await act(async () => {
      await logoutSession();
    });

    // then
    expect(queryClient.getQueryData(['rooms', 'me'])).toBeUndefined();
    expect(queryClient.getQueryCache().getAll()).toHaveLength(0);
  });
});
