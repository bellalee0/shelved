import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import type { RankingGetParams, RankingGetResponse } from './types';

/** 사건별 랭킹 (로그인 필요). 비공개·없는 사건은 404 CASE_NOT_FOUND */
export async function getRankings(caseId: number, params: RankingGetParams) {
  const response = await apiClient.get<CommonResponse<RankingGetResponse>>(
    `/api/rankings/${caseId}`,
    { params },
  );
  return response.data.data;
}
