import { useQuery } from '@tanstack/react-query';
import { getCases } from '../api';

/** 사건 카드 목록. 로그인하지 않아도 조회할 수 있다 */
export function useCases() {
  return useQuery({ queryKey: ['cases'], queryFn: getCases });
}
