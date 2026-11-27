// 백엔드 ranking 도메인 DTO와 같은 이름·필드의 타입 (docs/api/cases-ranking.md)
import type { PageResponse } from '../../shared/api/types';

export type RankingResponse = {
  /** 공동 순위 적용 (1·2·2·4) */
  rank: number;
  nickname: string;
  /** 일시정지를 제외한 누적 진행 시간 (정답 접수 시점까지) */
  durationSec: number;
  /** 정답 제출 접수 시각 */
  clearedAt: string;
};

export type RankingGetResponse = {
  caseId: number;
  displayTitle: string;
  /** 요청자의 최고 기록과 전체 기준 순위. 성공 기록이 없으면 null */
  myRanking: RankingResponse | null;
  /** 기록이 없으면 content: [] */
  rankingPage: PageResponse<RankingResponse>;
};

export type RankingGetParams = {
  /** 0부터 */
  page: number;
  size: number;
};
