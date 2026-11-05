import { ApiError, isApiError } from '../../shared/api/apiError';
import {
  getApiSessionId,
  resetApiSession,
  setAccessTokenGetter,
  setAuthFailureHandler,
  setTokenRefresher,
} from '../../shared/api/client';
import * as authApi from './api';
import { withAuthLock } from './authLock';
import {
  AUTH_USER_ID_STORAGE_KEY,
  readRefreshToken,
  REFRESH_TOKEN_STORAGE_KEY,
  removeRefreshToken,
  saveRefreshToken,
} from './refreshTokenStorage';
import { useAuthStore } from './store';
import type { AuthTokenResponse, UserMeResponse } from './types';

// 로그인 상태를 바꾸는 작업(로그인·재발급·복원·로그아웃)을 모은 곳. 화면은 hooks를 통해서만 부른다.
// 토큰 처리 흐름은 docs/api/auth-user.md "토큰 처리 흐름"·"여러 탭의 토큰 갱신 조정"을 따른다.

const INVALID_REFRESH_TOKEN = 'INVALID_REFRESH_TOKEN';

const authClearedListeners = new Set<() => void>();
let pendingRestore: Promise<void> | null = null;
// 진행 중인 인증 실패 정리. 동시에 여러 요청이 401을 받아도 정리는 한 번만 한다
let pendingAuthFailure: Promise<void> | null = null;
// 마지막으로 시작한 로그인 시도 번호. 뒤에 시작한 로그인이 있으면 앞의 로그인 응답은 버린다
let latestLoginAttempt = 0;

/** 공통 axios 인스턴스에 액세스 토큰 getter와 재발급 함수를 등록한다. 앱 시작 시 한 번 호출한다 */
export function connectAuthToApiClient() {
  setAccessTokenGetter(() => useAuthStore.getState().accessToken);
  setTokenRefresher(refreshSession);
  setAuthFailureHandler(handleAuthFailure);
}

/**
 * 401 INVALID_TOKEN·LOGIN_REQUIRED를 받았을 때 로그아웃과 같은 정리를 한다. 서버 무효화 실패는 무시한다.
 * 정리할 로그인 상태가 이미 없거나 정리가 진행 중이면 아무것도 하지 않는다.
 * 이전 세션 요청의 응답은 공통 클라이언트가 걸러 이 함수까지 오지 않는다.
 */
function handleAuthFailure() {
  if (pendingAuthFailure) {
    return;
  }
  const { accessToken, user } = useAuthStore.getState();
  if (!accessToken && !user && !readRefreshToken()) {
    return;
  }
  pendingAuthFailure = logoutSession()
    .catch(() => undefined)
    .finally(() => {
      pendingAuthFailure = null;
    });
}

/** 로그인 상태가 지워질 때(로그아웃·재발급 실패·다른 탭 로그아웃) 호출할 함수를 등록한다. Query 캐시 정리에 쓴다 */
export function subscribeAuthCleared(listener: () => void) {
  authClearedListeners.add(listener);
  return () => {
    authClearedListeners.delete(listener);
  };
}

/** 로그인 요청을 보내기 전에 만든 시도 식별자. 응답이 돌아왔을 때 아직 유효한 시도인지 가리는 데 쓴다 */
export interface LoginAttempt {
  readonly number: number;
  readonly sessionId: number;
}

/**
 * 로그인 요청(POST /api/auth/google)을 보내기 **전에** 부른다.
 * 요청 대기 중에 로그아웃하거나 더 늦게 시작한 로그인이 있으면, 이 시도의 응답은 completeLogin에서 버려진다.
 */
export function beginLoginAttempt(): LoginAttempt {
  latestLoginAttempt += 1;
  return { number: latestLoginAttempt, sessionId: getApiSessionId() };
}

/**
 * Google 로그인 응답을 반영한다. 리프레시 토큰은 다른 탭과 순서를 맞추기 위해 잠금 안에서 저장한다.
 * 요청·잠금을 기다리는 사이 로그아웃하거나 다른 로그인이 시작되었으면 이 응답은 반영하지 않고,
 * 받은 리프레시 토큰을 서버에서 무효화해 유효한 토큰이 남지 않게 한다.
 */
export async function completeLogin(attempt: LoginAttempt, response: AuthTokenResponse) {
  const applied = await withAuthLock(async () => {
    if (attempt.number !== latestLoginAttempt || getApiSessionId() !== attempt.sessionId) {
      return false;
    }
    // 이미 다른 사용자로 로그인한 상태였다면 이전 사용자의 요청·캐시를 먼저 정리한다
    clearAuthState();
    // 저장과 메모리 반영 사이에 await가 없어, 확인 후 끼어드는 로그아웃이 없다.
    // 이후 로그아웃은 메모리를 비운 뒤 잠금 안에서 방금 저장한 토큰을 읽어 무효화한다
    saveRefreshToken(response.refreshToken, response.user.userId);
    useAuthStore.setState({
      accessToken: response.accessToken,
      user: response.user,
      restoreStatus: 'ready',
      restoreErrorMessage: null,
    });
    return true;
  });

  if (!applied) {
    try {
      await authApi.logout({ refreshToken: response.refreshToken });
    } catch {
      // 무효화하지 못해도 이 토큰은 저장하지 않았으므로 이 브라우저에서는 쓰이지 않는다
    }
  }
}

/** 닉네임 변경 등으로 받은 최신 사용자 정보를 반영한다 */
export function applyUser(user: UserMeResponse) {
  if (useAuthStore.getState().user) {
    useAuthStore.setState({ user });
  }
}

/**
 * 리프레시 토큰으로 액세스 토큰을 재발급하고 새 액세스 토큰을 돌려준다.
 * 공통 axios 인스턴스의 401 EXPIRED_TOKEN 처리와 앱 시작 시 복원이 함께 쓴다.
 * - 잠금 안에서 localStorage의 최신 리프레시 토큰을 다시 읽어 쓰고, 새 토큰은 잠금을 풀기 전에 저장한다.
 * - INVALID_REFRESH_TOKEN이면 요청에 쓴 토큰과 현재 저장값을 비교한다.
 *   달라졌으면(다른 탭이 먼저 갱신) 최신 값으로 한 번만 다시 재발급하고, 같으면 토큰을 지우고 로그아웃 상태로 만든다.
 * - 네트워크·서버 오류는 토큰을 지우지 않고 그대로 던진다.
 * - 응답의 사용자가 메모리의 사용자와 다르면(다른 탭에서 다른 계정으로 로그인) 이전 사용자 상태를 정리한 뒤 반영한다.
 *   API 세션 번호가 바뀌므로 재발급을 기다리던 이전 사용자의 요청은 재전송되지 않고 취소된다.
 */
export function refreshSession(): Promise<string> {
  const sessionId = getApiSessionId();

  return withAuthLock(async () => {
    let usedToken = readRefreshToken();
    let retriedWithLatest = false;

    for (;;) {
      if (!usedToken) {
        // 다른 탭에서 로그아웃해 저장값이 이미 지워진 경우
        clearAuthState();
        throw createLoginRequiredError();
      }

      let response: AuthTokenResponse;
      try {
        response = await authApi.refreshToken({ refreshToken: usedToken });
      } catch (error) {
        if (!isApiError(error) || error.code !== INVALID_REFRESH_TOKEN) {
          throw error;
        }
        const latestToken = readRefreshToken();
        if (latestToken && latestToken !== usedToken && !retriedWithLatest) {
          usedToken = latestToken;
          retriedWithLatest = true;
          continue;
        }
        // 다른 탭이 저장한 새 값은 지우지 않는다
        if (latestToken === usedToken) {
          removeRefreshToken();
        }
        clearAuthState();
        throw error;
      }

      // 서버에서 이전 토큰은 이미 무효가 되었으므로, 기다리는 사이 로그아웃했더라도 새 토큰을 저장해 둔다.
      // 잠금 순서상 바로 뒤에 실행되는 로그아웃이 이 값을 읽어 서버에서 무효화하고 지운다.
      saveRefreshToken(response.refreshToken, response.user.userId);
      if (getApiSessionId() !== sessionId) {
        throw createLoginRequiredError();
      }
      const currentUser = useAuthStore.getState().user;
      if (currentUser && currentUser.userId !== response.user.userId) {
        clearAuthState();
      }
      useAuthStore.setState({ accessToken: response.accessToken, user: response.user });
      return response.accessToken;
    }
  });
}

/**
 * 새로고침 후 로그인 상태를 복원한다. 앱 시작 시 hooks/useAuthBootstrap 한 곳에서만 부른다.
 * 리프레시 토큰이 있으면 refresh → users/me 순서로 복원하고, 끝날 때까지 restoreStatus는 restoring이다.
 * 동시에 여러 번 불러도 진행 중인 복원 하나를 함께 기다린다.
 */
export function restoreSession(): Promise<void> {
  pendingRestore ??= runRestore().finally(() => {
    pendingRestore = null;
  });
  return pendingRestore;
}

async function runRestore() {
  if (!readRefreshToken()) {
    useAuthStore.setState({ restoreStatus: 'ready', restoreErrorMessage: null });
    return;
  }

  useAuthStore.setState({ restoreStatus: 'restoring', restoreErrorMessage: null });
  try {
    await refreshSession();
    const sessionId = getApiSessionId();
    const user = await authApi.getMyInfo();
    if (getApiSessionId() === sessionId) {
      useAuthStore.setState({ user });
    }
    useAuthStore.setState({ restoreStatus: 'ready' });
  } catch (error) {
    if (isApiError(error) && (error.isNetworkError || (error.status ?? 0) >= 500)) {
      // 토큰은 그대로 두고 재시도 안내를 보여준다
      useAuthStore.setState({ restoreStatus: 'restoreFailed', restoreErrorMessage: error.message });
      return;
    }
    // INVALID_REFRESH_TOKEN 등: 로그아웃 상태로 시작한다 (토큰 삭제 여부는 refreshSession이 정한다)
    clearAuthState();
    useAuthStore.setState({ restoreStatus: 'ready', restoreErrorMessage: null });
  }
}

/**
 * 로그아웃한다. 서버 무효화 요청이 실패해도 로컬 로그인 상태는 정리한다.
 * 메모리 상태는 바로 비워 화면과 진행 중인 요청을 먼저 끊고, 리프레시 토큰은 잠금 안에서 최신 값을 읽어 무효화·삭제한다.
 */
export async function logoutSession() {
  // force로 API 세션 번호를 바꾸므로, 응답을 기다리는 중인 로그인 시도(beginLoginAttempt가 세션 번호를 기록)도 무효가 된다
  clearAuthState({ force: true });
  await withAuthLock(async () => {
    const token = readRefreshToken();
    if (!token) {
      return;
    }
    try {
      await authApi.logout({ refreshToken: token });
    } catch {
      // 서버에서 무효화하지 못해도 이 브라우저에서는 토큰을 지운다
    } finally {
      removeRefreshToken();
    }
  });
}

/**
 * 다른 탭의 로그인 상태 변화를 storage 이벤트로 이 탭에 반영한다. 반환값은 감지 해제 함수다.
 * - 리프레시 토큰 삭제(로그아웃): 메모리 로그인 상태를 비운다.
 * - 저장된 userId가 이 탭의 사용자와 다름(다른 계정 로그인): 이전 사용자 상태를 정리하고 저장된 토큰으로 복원한다.
 * - 같은 사용자의 재발급(rotation)으로 토큰 값만 바뀜: 아무것도 하지 않는다.
 *   탭마다 rotation 때 다시 재발급하면 서로의 storage 이벤트로 끝없이 재발급하게 되기 때문이다.
 */
export function startAuthStorageSync() {
  function handleStorage(event: StorageEvent) {
    // key가 null이면 다른 탭이 localStorage.clear()를 호출한 경우다
    const isRefreshTokenRemoved =
      (event.key === REFRESH_TOKEN_STORAGE_KEY || event.key === null) && event.newValue === null;
    if (isRefreshTokenRemoved) {
      clearAuthState();
      return;
    }
    if (event.key === AUTH_USER_ID_STORAGE_KEY && event.newValue !== null) {
      const currentUser = useAuthStore.getState().user;
      if (currentUser && String(currentUser.userId) !== event.newValue) {
        clearAuthState();
        void restoreSession();
      }
    }
  }
  window.addEventListener('storage', handleStorage);
  return () => window.removeEventListener('storage', handleStorage);
}

/**
 * 메모리의 로그인 상태(액세스 토큰·사용자)를 비우고, 이전에 보낸 요청의 응답을 버리도록 API 세션을 초기화한다.
 * 로그인 상태가 없었으면 진행 중인 비로그인 요청(사건 목록 등)을 끊지 않도록 아무것도 하지 않는다.
 */
function clearAuthState({ force = false }: { force?: boolean } = {}) {
  const { accessToken, user } = useAuthStore.getState();
  if (!force && !accessToken && !user) {
    return;
  }
  resetApiSession();
  useAuthStore.setState({ accessToken: null, user: null });
  authClearedListeners.forEach((listener) => listener());
}

/** 재발급할 리프레시 토큰이 없을 때의 에러. 서버 응답이 없으므로 errors.md LOGIN_REQUIRED와 같은 문구를 쓴다 */
function createLoginRequiredError() {
  return new ApiError({
    status: 401,
    message: '로그인이 필요합니다.',
    code: 'LOGIN_REQUIRED',
    detail: null,
    isNetworkError: false,
  });
}
