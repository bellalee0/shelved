import axios, { AxiosError } from 'axios';
import { ApiError, NETWORK_ERROR_MESSAGE } from './apiError';

declare module 'axios' {
  interface AxiosRequestConfig {
    /**
     * true면 X-Connection-Id 헤더를 붙인다. 세션 조작 API(하트비트, NPC 질문, 노트 저장 등)의
     * 도메인 API 함수에서 표시한다. 대상 목록은 docs/api/README.md "X-Connection-Id" 표를 따른다.
     */
    requiresConnectionId?: boolean;
  }
}

/** 응답이 공통 형식이 아니라 백엔드 message를 꺼낼 수 없을 때 쓰는 문구 (예: 프록시가 돌려준 HTML 오류 페이지) */
const UNEXPECTED_ERROR_MESSAGE = '요청을 처리하지 못했습니다. 잠시 후 다시 시도해 주세요.';

let getAccessToken: (() => string | null) | null = null;
let connectionId: string | null = null;

/**
 * 액세스 토큰을 꺼내는 함수를 등록한다. 인증 상태 저장소(Zustand)가 앱 시작 시 한 번 등록한다.
 * 등록된 함수가 없거나 null을 돌려주면 Authorization 헤더를 붙이지 않는다.
 */
export function setAccessTokenGetter(getter: (() => string | null) | null) {
  getAccessToken = getter;
}

/** 시작·재개 API가 발급한 connectionId를 메모리에 보관한다. 화면을 떠나거나 연결이 끝나면 null로 지운다 */
export function setConnectionId(id: string | null) {
  connectionId = id;
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
  const accessToken = getAccessToken?.();
  if (accessToken) {
    config.headers.set('Authorization', `Bearer ${accessToken}`);
  }
  // connectionId가 없으면 헤더 없이 보내고, 서버가 400 CONNECTION_ID_REQUIRED로 알려준다
  if (config.requiresConnectionId && connectionId) {
    config.headers.set('X-Connection-Id', connectionId);
  }
  return config;
});

apiClient.interceptors.response.use(
  // 성공 응답은 axios 응답(status, headers, data)을 그대로 돌려준다. data.data는 도메인 API 함수가 꺼낸다
  (response) => response,
  (error: unknown) => {
    // TODO(FE-2): 401 EXPIRED_TOKEN일 때만 토큰 재발급 후 원래 요청을 한 번 재전송한다.
    //  - 동시에 만료된 요청은 하나의 재발급 Promise를 함께 기다린다.
    //  - 로그인·재발급 요청 자체와 이미 재전송한 요청에는 적용하지 않는다.
    //  - 재전송 시 원래 본문·requestId·X-Connection-Id를 유지한다.
    //  재발급이 필요 없으면 아래 toApiError 변환으로 넘어간다.
    return Promise.reject(toApiError(error));
  },
);

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
