import { describe, expect, it } from 'vitest';
import { ApiError } from '../shared/api/apiError';
import { createQueryClient } from './queryClient';

function createApiError(status: number | null) {
  return new ApiError({
    status,
    message: '오류',
    code: null,
    detail: null,
    isNetworkError: status === null,
  });
}

function getQueryRetry() {
  const retry = createQueryClient().getDefaultOptions().queries?.retry;
  if (typeof retry !== 'function') {
    throw new Error('조회 재시도 설정이 함수가 아닙니다.');
  }
  return retry;
}

describe('createQueryClient', () => {
  it('조회가 4xx로 실패하면 재시도하지 않는다', () => {
    // given
    const retry = getQueryRetry();

    // when
    const result = retry(0, createApiError(404));

    // then
    expect(result).toBe(false);
  });

  it('조회가 5xx나 네트워크 오류로 실패하면 3번까지 재시도한다', () => {
    // given
    const retry = getQueryRetry();

    // when & then
    expect(retry(0, createApiError(502))).toBe(true);
    expect(retry(2, createApiError(null))).toBe(true);
    expect(retry(3, createApiError(500))).toBe(false);
  });

  it('변경 요청은 자동 재시도하지 않는다', () => {
    // given
    const queryClient = createQueryClient();

    // when
    const retry = queryClient.getDefaultOptions().mutations?.retry;

    // then
    expect(retry).toBe(false);
  });
});
