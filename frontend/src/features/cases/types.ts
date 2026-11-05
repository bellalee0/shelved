// 백엔드 cases 도메인 DTO와 같은 이름·필드의 타입 (docs/api/cases-ranking.md)
import type { Difficulty } from '../../shared/api/types';

/** 메인 화면의 사건 카드 한 장. 원작 제목·작가 등 정답 정보는 포함하지 않는다 */
export type CaseCardResponse = {
  caseId: number;
  /** 원작과 무관한 독자 제목 */
  displayTitle: string;
  difficulty: Difficulty;
  keywordTags: string[];
  coverImageUrl: string | null;
  timeLimitSec: number;
  hintLimit: number;
  /** 지목 기회(3). 카드에 "지목 3회"로 표시한다 */
  accusationLimit: number;
};
