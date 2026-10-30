import { QueryClient } from '@tanstack/react-query';
import { isApiError } from '../shared/api/apiError';

/** 조회 실패 시 자동 재시도 최대 횟수 (TanStack Query 기본값과 같음) */
const MAX_QUERY_RETRY_COUNT = 3;

/**
 * TanStack Query 전역 설정.
 * - 조회: 4xx는 다시 보내도 결과가 같으므로 재시도하지 않고, 네트워크 오류·5xx만 재시도한다.
 * - 변경(mutation): 자동 재시도하지 않는다. AI 요청 재시도는 사용자가 같은 requestId로 직접 다시 보낸다.
 */
export function createQueryClient() {
  return new QueryClient({
    defaultOptions: {
      queries: {
        retry: (failureCount, error) => {
          if (isApiError(error) && error.status !== null && error.status < 500) {
            return false;
          }
          return failureCount < MAX_QUERY_RETRY_COUNT;
        },
      },
      mutations: {
        retry: false,
      },
    },
  });
}
