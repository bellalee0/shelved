/** 백엔드 응답을 받지 못했을 때(네트워크 오류·서버 미실행 등)만 쓰는 프론트엔드 공통 문구 */
export const NETWORK_ERROR_MESSAGE =
  '서버에 연결할 수 없습니다. 네트워크 상태를 확인한 뒤 다시 시도해 주세요.';

/**
 * 공통 API 에러.
 * - 백엔드가 실패 응답을 보낸 경우: status·message·code·detail을 응답 그대로 보존한다.
 * - 응답 자체가 없는 경우(isNetworkError=true): status·code는 null, message는 공통 문구.
 * 화면 분기는 code로 하고, 사용자에게는 message를 그대로 보여준다.
 */
export class ApiError extends Error {
  readonly status: number | null;
  readonly code: string | null;
  readonly detail: unknown;
  readonly isNetworkError: boolean;

  constructor(params: {
    status: number | null;
    message: string;
    code: string | null;
    detail: unknown;
    isNetworkError: boolean;
    cause?: unknown;
  }) {
    super(params.message, { cause: params.cause });
    this.name = 'ApiError';
    this.status = params.status;
    this.code = params.code;
    this.detail = params.detail;
    this.isNetworkError = params.isNetworkError;
  }
}

export function isApiError(error: unknown): error is ApiError {
  return error instanceof ApiError;
}
