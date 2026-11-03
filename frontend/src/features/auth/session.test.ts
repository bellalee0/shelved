import { http, HttpResponse } from 'msw';
import { CanceledError } from 'axios';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { mockErrorResponse } from '../../mocks/handlers/auth';
import { createMockGoogleIdToken } from '../../mocks/mockGoogleIdToken';
import { server } from '../../mocks/node';
import { NETWORK_ERROR_MESSAGE } from '../../shared/api/apiError';
import {
  apiClient,
  getApiSessionId,
  setAccessTokenGetter,
  setConnectionId,
  setTokenRefresher,
} from '../../shared/api/client';
import { loginWithGoogle } from './api';
import { AUTH_LOCK_NAME } from './authLock';
import {
  AUTH_USER_ID_STORAGE_KEY,
  readRefreshToken,
  REFRESH_TOKEN_STORAGE_KEY,
  saveRefreshToken,
} from './refreshTokenStorage';
import {
  beginLoginAttempt,
  completeLogin,
  connectAuthToApiClient,
  logoutSession,
  refreshSession,
  restoreSession,
  startAuthStorageSync,
  subscribeAuthCleared,
} from './session';
import { INITIAL_AUTH_STATE, useAuthStore } from './store';
import { installFakeWebLocks } from './testing/fakeWebLocks';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const INVALID_REFRESH = () =>
  mockErrorResponse(
    401,
    'INVALID_REFRESH_TOKEN',
    '로그인 정보가 만료되었습니다. 다시 로그인해 주세요.',
  );

let fakeLocks: ReturnType<typeof installFakeWebLocks>;

beforeEach(() => {
  useAuthStore.setState(INITIAL_AUTH_STATE, true);
  fakeLocks = installFakeWebLocks();
  connectAuthToApiClient();
});

afterEach(() => {
  fakeLocks.uninstall();
  setAccessTokenGetter(null);
  setTokenRefresher(null);
  setConnectionId(null);
});

/** 목 서버로 Google 로그인한 상태를 만든다 */
async function loginAs(googleSub: string) {
  const attempt = beginLoginAttempt();
  await completeLogin(
    attempt,
    await loginWithGoogle({ idToken: createMockGoogleIdToken(googleSub) }),
  );
}

/** 새로고침처럼 메모리 상태만 비운다 (localStorage의 리프레시 토큰은 남는다) */
function simulateReload() {
  useAuthStore.setState(INITIAL_AUTH_STATE, true);
}

/** 재발급 요청에 쓰인 리프레시 토큰을 기록한다 */
function recordRefreshRequests() {
  const usedTokens: string[] = [];
  server.events.on('request:start', async ({ request }) => {
    if (request.url.endsWith('/api/auth/refresh')) {
      const body = (await request.clone().json()) as { refreshToken: string };
      usedTokens.push(body.refreshToken);
    }
  });
  return usedTokens;
}

afterEach(() => {
  server.events.removeAllListeners();
});

describe('restoreSession 로그인 복원', () => {
  it('저장된 리프레시 토큰이 없으면 요청 없이 비로그인 상태로 시작한다', async () => {
    // given
    const usedTokens = recordRefreshRequests();

    // when
    await restoreSession();

    // then
    const state = useAuthStore.getState();
    expect(state.restoreStatus).toBe('ready');
    expect(state.user).toBeNull();
    expect(usedTokens).toHaveLength(0);
  });

  it('리프레시 토큰이 있으면 refresh → users/me 순서로 로그인 상태를 복원한다', async () => {
    // given
    await loginAs('restore-player');
    const tokenBeforeReload = readRefreshToken();
    simulateReload();
    const requestPaths: string[] = [];
    server.events.on('request:start', ({ request }) => {
      requestPaths.push(new URL(request.url).pathname);
    });

    // when
    const restoring = restoreSession();
    const statusWhileRestoring = useAuthStore.getState().restoreStatus;
    await restoring;

    // then
    const state = useAuthStore.getState();
    expect(statusWhileRestoring).toBe('restoring');
    expect(requestPaths).toEqual(['/api/auth/refresh', '/api/users/me']);
    expect(state.restoreStatus).toBe('ready');
    expect(state.accessToken).not.toBeNull();
    expect(state.user?.nicknameRequired).toBe(true);
    // rotation으로 새 리프레시 토큰이 저장된다
    expect(readRefreshToken()).not.toBe(tokenBeforeReload);
  });

  it('INVALID_REFRESH_TOKEN이면 토큰을 지우고 비로그인 상태로 시작한다', async () => {
    // given
    saveRefreshToken('revoked-refresh-token', 9);

    // when
    await restoreSession();

    // then
    const state = useAuthStore.getState();
    expect(state.restoreStatus).toBe('ready');
    expect(state.user).toBeNull();
    expect(readRefreshToken()).toBeNull();
  });

  it('네트워크 오류면 토큰을 지우지 않고 재시도 안내 상태가 된다', async () => {
    // given
    saveRefreshToken('kept-refresh-token', 9);
    server.use(http.post(`${API_BASE_URL}/api/auth/refresh`, () => HttpResponse.error()));

    // when
    await restoreSession();

    // then
    const state = useAuthStore.getState();
    expect(state.restoreStatus).toBe('restoreFailed');
    expect(state.restoreErrorMessage).toBe(NETWORK_ERROR_MESSAGE);
    expect(readRefreshToken()).toBe('kept-refresh-token');
  });
});

describe('refreshSession 여러 탭 조정', () => {
  it('Web Locks의 shelved-auth 잠금 안에서 재발급한다', async () => {
    // given
    await loginAs('lock-player');
    fakeLocks.requestedNames.length = 0;

    // when
    await refreshSession();

    // then
    expect(fakeLocks.requestedNames).toEqual([AUTH_LOCK_NAME]);
  });

  it('두 재발급이 겹치면 순서대로 실행되어 뒤의 재발급은 앞에서 저장한 최신 토큰을 쓴다', async () => {
    // given
    await loginAs('serial-player');
    const firstToken = readRefreshToken();
    const usedTokens = recordRefreshRequests();

    // when
    await Promise.all([refreshSession(), refreshSession()]);

    // then
    expect(usedTokens).toHaveLength(2);
    expect(usedTokens[0]).toBe(firstToken);
    expect(usedTokens[1]).not.toBe(firstToken);
    expect(useAuthStore.getState().user).not.toBeNull();
  });

  it('INVALID_REFRESH_TOKEN인데 저장값이 바뀌었으면 최신 값으로 한 번 다시 재발급한다', async () => {
    // given
    saveRefreshToken('stale-token', 9);
    const usedTokens = recordRefreshRequests();
    server.use(
      http.post(`${API_BASE_URL}/api/auth/refresh`, async ({ request }) => {
        const { refreshToken } = (await request.json()) as { refreshToken: string };
        if (refreshToken === 'stale-token') {
          // 다른 탭이 먼저 재발급해 저장값을 바꾼 상황
          localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, 'latest-token');
          return INVALID_REFRESH();
        }
        return HttpResponse.json({
          success: true,
          message: '토큰 재발급 성공',
          data: {
            accessToken: 'access-from-latest',
            refreshToken: 'rotated-token',
            accessTokenExpiresInSec: 3600,
            user: { userId: 9, nickname: '왓슨', role: 'PLAYER', nicknameRequired: false },
          },
          timestamp: '',
        });
      }),
    );

    // when
    const accessToken = await refreshSession();

    // then
    expect(usedTokens).toEqual(['stale-token', 'latest-token']);
    expect(accessToken).toBe('access-from-latest');
    expect(readRefreshToken()).toBe('rotated-token');
  });

  it('INVALID_REFRESH_TOKEN이고 저장값이 그대로면 토큰을 지우고 로그아웃 상태가 된다', async () => {
    // given
    await loginAs('expired-player');
    const usedTokens = recordRefreshRequests();
    server.use(http.post(`${API_BASE_URL}/api/auth/refresh`, () => INVALID_REFRESH()));

    // when
    const result = refreshSession();

    // then
    await expect(result).rejects.toMatchObject({ code: 'INVALID_REFRESH_TOKEN' });
    expect(usedTokens).toHaveLength(1);
    expect(readRefreshToken()).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('최신 값으로 다시 보내도 실패하면 더 재시도하지 않고, 다른 탭이 저장한 값은 지우지 않는다', async () => {
    // given
    saveRefreshToken('token-0', 9);
    const usedTokens = recordRefreshRequests();
    let rotation = 0;
    server.use(
      http.post(`${API_BASE_URL}/api/auth/refresh`, () => {
        rotation += 1;
        localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, `token-${rotation}`);
        return INVALID_REFRESH();
      }),
    );

    // when
    const result = refreshSession();

    // then
    await expect(result).rejects.toMatchObject({ code: 'INVALID_REFRESH_TOKEN' });
    expect(usedTokens).toEqual(['token-0', 'token-1']);
    expect(readRefreshToken()).toBe('token-2');
  });

  it('네트워크 오류면 토큰을 지우지 않는다', async () => {
    // given
    await loginAs('network-player');
    const storedToken = readRefreshToken();
    server.use(http.post(`${API_BASE_URL}/api/auth/refresh`, () => HttpResponse.error()));

    // when
    const result = refreshSession();

    // then
    await expect(result).rejects.toMatchObject({ isNetworkError: true });
    expect(readRefreshToken()).toBe(storedToken);
    expect(useAuthStore.getState().user).not.toBeNull();
  });
});

describe('startAuthStorageSync 다른 탭 로그아웃 반영', () => {
  it('다른 탭이 리프레시 토큰을 지우면 이 탭의 메모리 로그인 상태도 비운다', async () => {
    // given
    await loginAs('tab-player');
    const stop = startAuthStorageSync();

    // when
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: REFRESH_TOKEN_STORAGE_KEY,
        newValue: null,
        oldValue: 'x',
      }),
    );
    stop();

    // then
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
  });

  it('다른 탭의 재발급으로 값만 바뀌면 로그인 상태를 유지한다', async () => {
    // given
    await loginAs('rotate-player');
    const stop = startAuthStorageSync();

    // when
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: REFRESH_TOKEN_STORAGE_KEY,
        newValue: 'rotated-in-other-tab',
        oldValue: 'x',
      }),
    );
    stop();

    // then
    expect(useAuthStore.getState().user).not.toBeNull();
  });
});

describe('logoutSession 로그아웃', () => {
  it('서버에 리프레시 토큰 무효화를 요청하고 토큰·사용자·connectionId를 정리한다', async () => {
    // given
    await loginAs('logout-player');
    const storedToken = readRefreshToken();
    setConnectionId('conn-before-logout');
    const logoutBodies: unknown[] = [];
    server.events.on('request:start', async ({ request }) => {
      if (request.url.endsWith('/api/auth/logout')) {
        logoutBodies.push(await request.clone().json());
      }
    });

    // when
    await logoutSession();

    // then
    expect(logoutBodies).toEqual([{ refreshToken: storedToken }]);
    expect(readRefreshToken()).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    server.use(
      http.post(`${API_BASE_URL}/api/rooms/1/heartbeat`, ({ request }) =>
        HttpResponse.json({ connectionId: request.headers.get('X-Connection-Id') }),
      ),
    );
    const response = await apiClient.post('/api/rooms/1/heartbeat', null, {
      requiresConnectionId: true,
    });
    expect(response.data.connectionId).toBeNull();
  });

  it('로그아웃 요청이 실패해도 로컬 로그인 상태는 정리한다', async () => {
    // given
    await loginAs('offline-player');
    server.use(http.post(`${API_BASE_URL}/api/auth/logout`, () => HttpResponse.error()));

    // when
    await logoutSession();

    // then
    expect(readRefreshToken()).toBeNull();
    expect(useAuthStore.getState().user).toBeNull();
  });
});

describe('계정 변경 감지', () => {
  /** 다른 탭에서 다른 사용자로 로그인해 저장소 값만 바뀐 상황을 만든다 */
  async function loginInOtherTab(googleSub: string) {
    const response = await loginWithGoogle({ idToken: createMockGoogleIdToken(googleSub) });
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, response.refreshToken);
    localStorage.setItem(AUTH_USER_ID_STORAGE_KEY, String(response.user.userId));
    return response.user;
  }

  it('재발급 응답의 사용자가 다르면 이전 사용자 상태를 정리하고, 기다리던 요청은 재전송하지 않는다', async () => {
    // given
    await loginAs('account-a');
    const userA = useAuthStore.getState().user!;
    const userB = await loginInOtherTab('account-b');
    const sessionBefore = getApiSessionId();
    const cleared = vi.fn();
    const unsubscribe = subscribeAuthCleared(cleared);
    let protectedCalls = 0;
    server.use(
      http.get(`${API_BASE_URL}/api/test/protected`, () => {
        protectedCalls += 1;
        return mockErrorResponse(401, 'EXPIRED_TOKEN', '로그인이 만료되었습니다.');
      }),
    );

    // when
    const result = apiClient.get('/api/test/protected');

    // then
    await expect(result).rejects.toBeInstanceOf(CanceledError);
    unsubscribe();
    expect(protectedCalls).toBe(1);
    expect(getApiSessionId()).toBeGreaterThan(sessionBefore);
    expect(cleared).toHaveBeenCalled();
    expect(useAuthStore.getState().user?.userId).toBe(userB.userId);
    expect(userB.userId).not.toBe(userA.userId);
  });

  it('storage 이벤트로 다른 사용자를 감지하면 정리한 뒤 그 사용자로 복원한다', async () => {
    // given
    await loginAs('storage-a');
    const userB = await loginInOtherTab('storage-b');
    const cleared = vi.fn();
    const unsubscribe = subscribeAuthCleared(cleared);
    const stop = startAuthStorageSync();

    // when
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: AUTH_USER_ID_STORAGE_KEY,
        newValue: String(userB.userId),
        oldValue: 'x',
      }),
    );
    await restoreSession();
    stop();
    unsubscribe();

    // then
    expect(cleared).toHaveBeenCalledTimes(1);
    expect(useAuthStore.getState().user?.userId).toBe(userB.userId);
    expect(useAuthStore.getState().restoreStatus).toBe('ready');
  });

  it('같은 사용자의 토큰 값만 바뀌면 재발급하지 않는다', async () => {
    // given
    await loginAs('same-user');
    const user = useAuthStore.getState().user!;
    const usedTokens = recordRefreshRequests();
    const stop = startAuthStorageSync();

    // when
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: REFRESH_TOKEN_STORAGE_KEY,
        newValue: 'rotated-in-other-tab',
        oldValue: 'x',
      }),
    );
    window.dispatchEvent(
      new StorageEvent('storage', {
        key: AUTH_USER_ID_STORAGE_KEY,
        newValue: String(user.userId),
        oldValue: String(user.userId),
      }),
    );
    await new Promise((resolve) => setTimeout(resolve, 20));
    stop();

    // then
    expect(usedTokens).toHaveLength(0);
    expect(useAuthStore.getState().user?.userId).toBe(user.userId);
  });
});

describe('completeLogin 로그인 응답 반영', () => {
  /** 다른 탭이 잠금을 잡고 있는 상황을 만든다. 반환한 함수를 부르면 잠금을 푼다 */
  function holdAuthLock() {
    let release!: () => void;
    const held = new Promise<void>((resolve) => {
      release = resolve;
    });
    void navigator.locks.request(AUTH_LOCK_NAME, () => held);
    return release;
  }

  function recordLogoutTokens() {
    const tokens: string[] = [];
    server.events.on('request:start', async ({ request }) => {
      if (request.url.endsWith('/api/auth/logout')) {
        const body = (await request.clone().json()) as { refreshToken: string };
        tokens.push(body.refreshToken);
      }
    });
    return tokens;
  }

  it('잠금을 기다리는 사이 로그아웃하면 로그인을 반영하지 않고 받은 토큰을 서버에서 무효화한다', async () => {
    // given
    const attempt = beginLoginAttempt();
    const response = await loginWithGoogle({ idToken: createMockGoogleIdToken('abandoned') });
    const logoutTokens = recordLogoutTokens();
    const release = holdAuthLock();

    // when
    const login = completeLogin(attempt, response);
    const logout = logoutSession();
    release();
    await Promise.all([login, logout]);

    // then
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(readRefreshToken()).toBeNull();
    expect(logoutTokens).toEqual([response.refreshToken]);
  });

  it('잠금을 기다리는 사이 다른 로그인이 시작되면 앞의 로그인 응답은 버리고 무효화한다', async () => {
    // given
    const firstAttempt = beginLoginAttempt();
    const first = await loginWithGoogle({ idToken: createMockGoogleIdToken('first-login') });
    const secondAttempt = beginLoginAttempt();
    const second = await loginWithGoogle({ idToken: createMockGoogleIdToken('second-login') });
    const logoutTokens = recordLogoutTokens();
    const release = holdAuthLock();

    // when
    const firstLogin = completeLogin(firstAttempt, first);
    const secondLogin = completeLogin(secondAttempt, second);
    release();
    await Promise.all([firstLogin, secondLogin]);

    // then
    expect(useAuthStore.getState().user?.userId).toBe(second.user.userId);
    expect(readRefreshToken()).toBe(second.refreshToken);
    expect(logoutTokens).toEqual([first.refreshToken]);
  });

  it('로그인 요청을 기다리는 사이 로그아웃하면 응답이 도착해도 로그인되지 않고 받은 토큰을 무효화한다', async () => {
    // given: 로그인 전 상태에서 요청을 보낸 시점
    const logoutTokens = recordLogoutTokens();
    const attempt = beginLoginAttempt();
    const response = await loginWithGoogle({
      idToken: createMockGoogleIdToken('logout-in-flight'),
    });

    // when: 응답을 반영하기 전에 로그아웃
    await logoutSession();
    await completeLogin(attempt, response);

    // then
    expect(useAuthStore.getState().user).toBeNull();
    expect(useAuthStore.getState().accessToken).toBeNull();
    expect(readRefreshToken()).toBeNull();
    expect(logoutTokens).toEqual([response.refreshToken]);
  });

  it('로그인 응답이 역순으로 도착하면 나중에 시작한 시도만 반영하고 먼저 시작한 시도는 무효화한다', async () => {
    // given: 두 요청을 차례로 시작
    const logoutTokens = recordLogoutTokens();
    const firstAttempt = beginLoginAttempt();
    const secondAttempt = beginLoginAttempt();
    const first = await loginWithGoogle({ idToken: createMockGoogleIdToken('first-in-flight') });
    const second = await loginWithGoogle({ idToken: createMockGoogleIdToken('second-in-flight') });

    // when: 나중에 시작한 쪽 응답이 먼저 도착
    await completeLogin(secondAttempt, second);
    await completeLogin(firstAttempt, first);

    // then
    expect(useAuthStore.getState().user?.userId).toBe(second.user.userId);
    expect(readRefreshToken()).toBe(second.refreshToken);
    expect(logoutTokens).toEqual([first.refreshToken]);
  });
});
