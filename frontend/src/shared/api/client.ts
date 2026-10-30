import axios, { AxiosError, CanceledError, type InternalAxiosRequestConfig } from 'axios';
import { ApiError, isApiError, NETWORK_ERROR_MESSAGE } from './apiError';

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * true면 X-Connection-Id 헤더를 붙인다. 세션 조작 API(하트비트, NPC 질문, 노트 저장 등)의
     * 도메인 API 함수에서 표시한다. 대상 목록은 docs/api/README.md "X-Connection-Id" 표를 따른다.
     */
    requiresConnectionId?: boolean;
    /**
     * true면 Authorization 헤더를 붙이지 않고, 401이어도 토큰 재발급을 하지 않는다.
     * 로그인·재발급·로그아웃처럼 토큰 없이 부르는 인증 API 함수에서 표시한다.
     */
    skipAuth?: boolean;
    /** 인터셉터 내부용. 토큰 재발급 후 재전송한 요청이면 true (다시 재발급하지 않는다) */
    authRetried?: boolean;
    /** 인터셉터 내부용. 요청을 처음 보낼 때의 API 세션 번호 (resetApiSession 참고) */
    apiSessionId?: number;
  }
}

/** 응답이 공통 형식이 아니라 백엔드 message를 꺼낼 수 없을 때 쓰는 문구 (예: 프록시가 돌려준 HTML 오류 페이지) */
const UNEXPECTED_ERROR_MESSAGE = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';

/** 재발급한 액세스 토큰을 돌려주는 함수. 실패하면 ApiError를 던진다 */
export type TokenRefresher = () => Promise<string>;

let getAccessToken: (() => string | null) | null = null;
let refreshAccessToken: TokenRefresher | null = null;
let connectionId: string | null = null;
let apiSessionId = 0;
// 동시에 만료된 요청들이 함께 기다리는 재발급 Promise. 재발급이 끝나면 비운다
let pendingRefresh: Promise<string> | null = null;

/**
 * 액세스 토큰을 꺼내는 함수를 등록한다. 인증 상태 저장소(Zustand)가 앱 시작 시 한 번 등록한다.
 * 등록된 함수가 없거나 null을 돌려주면 Authorization 헤더를 붙이지 않는다.
 */
export function setAccessTokenGetter(getter: (() => string | null) | null) {
  getAccessToken = getter;
}

/**
 * 401 EXPIRED_TOKEN일 때 호출할 토큰 재발급 함수를 등록한다. 인증 기능(features/auth)이 앱 시작 시 한 번 등록한다.
 * shared가 도메인 코드를 직접 import하지 않도록, 토큰 getter와 같이 바깥에서 넣어 주는 방식으로 연결한다.
 */
export function setTokenRefresher(refresher: TokenRefresher | null) {
  refreshAccessToken = refresher;
}

/** 시작·재개 API가 발급한 connectionId를 메모리에 보관한다. 화면을 떠나거나 연결이 끝나면 null로 지운다 */
export function setConnectionId(id: string | null) {
  connectionId = id;
}

/**
 * 로그아웃·사용자 변경 시 호출한다. connectionId를 지우고 API 세션 번호를 올린다.
 * 이전 번호로 보낸 요청의 응답은 늦게 도착해도 취소(CanceledError)로 바꿔, 이전 사용자의 응답이
 * Query 캐시에 들어가거나 토큰 재발급·재전송을 일으키지 않게 한다.
 */
export function resetApiSession() {
  apiSessionId += 1;
  connectionId = null;
}

/** 현재 API 세션 번호. 인증 기능이 응답을 상태에 반영해도 되는지 확인할 때 쓴다 */
export function getApiSessionId() {
  return apiSessionId;
}

/**
 * 모든 API 요청에 쓰는 공통 axios 인스턴스. 컴포넌트·훅에서 axios를 직접 쓰지 않는다.
 * Content-Type은 지정하지 않는다. axios가 일반 객체는 application/json으로 보내고,
 * FormData(파일 업로드)는 브라우저가 multipart 경계값을 붙이도록 둔다.
 */
export const apiClient = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL,
});

apiClient.interceptors.request.use((config) => {
  if (!config.skipAuth) {
    const accessToken = getAccessToken?.();
    if (accessToken) {
      config.headers.set('Authorization', `Bearer ${accessToken}`);
    }
  }
  // 재전송 요청은 처음 보낼 때의 세션 번호와 X-Connection-Id 헤더를 그대로 유지한다
  if (!config.authRetried) {
    config.apiSessionId = apiSessionId;
    // connectionId가 없으면 헤더 없이 보내고, 서버가 400 CONNECTION_ID_REQUIRED로 알려준다
    if (config.requiresConnectionId && connectionId) {
      config.headers.set('X-Connection-Id', connectionId);
    }
  }
  return config;
});

apiClient.interceptors.response.use(
  // 성공 응답은 axios 응답(status, headers, data)을 그대로 돌려준다. data.data는 도메인 API 함수가 꺼낸다
  (response) => {
    if (isStaleRequest(response.config)) {
      return Promise.reject(createStaleResponseError());
    }
    return response;
  },
  async (error: unknown) => {
    const config = error instanceof AxiosError ? error.config : undefined;
    if (config && isStaleRequest(config)) {
      return Promise.reject(createStaleResponseError());
    }
    if (!config || !shouldRefreshToken(error, config)) {
      return Promise.reject(toApiError(error));
    }

    // 이 요청이 실패하는 사이 다른 요청이 이미 재발급을 마쳤으면, 다시 재발급하지 않고 새 토큰으로 재전송만 한다
    const latestAccessToken = getAccessToken?.();
    const alreadyRefreshed =
      !!latestAccessToken && config.headers.get('Authorization') !== `Bearer ${latestAccessToken}`;
    if (!alreadyRefreshed) {
      try {
        await refreshSharedAccessToken();
      } catch (refreshError) {
        return Promise.reject(isApiError(refreshError) ? refreshError : toApiError(error));
      }
    }
    // 재발급을 기다리는 사이 로그아웃했으면 이전 사용자 요청을 다시 보내지 않는다
    if (isStaleRequest(config)) {
      return Promise.reject(createStaleResponseError());
    }
    // config에는 처음 보낼 때 변환한 본문(requestId 포함)과 헤더가 그대로 들어 있다.
    // Authorization만 요청 인터셉터가 새 토큰으로 바꿔 붙인다
    return apiClient.request({ ...config, authRetried: true });
  },
);

/** 401 EXPIRED_TOKEN이고, 인증 API·이미 재전송한 요청이 아니며, 재발급 함수가 등록된 경우에만 재발급한다 */
function shouldRefreshToken(error: unknown, config: InternalAxiosRequestConfig): boolean {
  if (config.skipAuth || config.authRetried || !refreshAccessToken) {
    return false;
  }
  if (!(error instanceof AxiosError) || error.response?.status !== 401) {
    return false;
  }
  const body: unknown = error.response.data;
  return readString(isRecord(body) ? body.data : null, 'code') === 'EXPIRED_TOKEN';
}

function refreshSharedAccessToken(): Promise<string> {
  if (!pendingRefresh) {
    const refresher = refreshAccessToken!;
    pendingRefresh = refresher().finally(() => {
      pendingRefresh = null;
    });
  }
  return pendingRefresh;
}

function isStaleRequest(config: InternalAxiosRequestConfig): boolean {
  // 인증 API 응답은 인증 기능이 직접 세션 번호를 확인해 반영 여부를 정하므로 버리지 않는다
  // (예: 로그아웃 직전에 끝난 재발급의 새 리프레시 토큰은 저장해야 로그아웃 때 무효화할 수 있다)
  return (
    !config.skipAuth && config.apiSessionId !== undefined && config.apiSessionId !== apiSessionId
  );
}

function createStaleResponseError() {
  return new CanceledError('로그아웃 이전에 보낸 요청의 응답이라 무시합니다.');
}

/** axios 에러를 공통 ApiError로 바꾼다. 요청 취소처럼 API 실패가 아닌 에러는 그대로 둔다 */
function toApiError(error: unknown): unknown {
  if (!(error instanceof AxiosError) || axios.isCancel(error)) {
    return error;
  }

  const response = error.response;
  if (!response) {
    return new ApiError({
      status: null,
      message: NETWORK_ERROR_MESSAGE,
      code: null,
      detail: null,
      isNetworkError: true,
      cause: error,
    });
  }

  const body: unknown = response.data;
  const message = readString(body, 'message') ?? UNEXPECTED_ERROR_MESSAGE;
  const errorData = isRecord(body) ? body.data : null;

  return new ApiError({
    status: response.status,
    message,
    code: readString(errorData, 'code'),
    detail: isRecord(errorData) ? (errorData.detail ?? null) : null,
    isNetworkError: false,
    cause: error,
  });
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function readString(value: unknown, key: string): string | null {
  if (!isRecord(value)) {
    return null;
  }
  const field = value[key];
  return typeof field === 'string' ? field : null;
}
