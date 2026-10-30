import { QueryClient } from '@tanstack/react-query';

/** TanStack Query 전역 설정 */
export function createQueryClient() {
  return new QueryClient();
}
