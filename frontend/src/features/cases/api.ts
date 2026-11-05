import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import type { CaseCardResponse } from './types';

/** 출시된 사건 카드 전체 목록. 로그인하지 않아도 부를 수 있다 (페이지네이션 없음) */
export async function getCases() {
  const response = await apiClient.get<CommonResponse<CaseCardResponse[]>>('/api/cases');
  return response.data.data;
}
