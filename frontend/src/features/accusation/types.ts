// 백엔드 accusation 도메인 DTO와 같은 이름·필드의 타입 (docs/api/hint-accusation.md "지목")
import type { AccusationResponse, RoomStatus } from '../../shared/api/types';
import type { UsageCount } from '../room/types';

// AccusationResponse는 GET state의 pendingAccusation과 함께 쓰므로 shared에 있다
export type { AccusationResponse } from '../../shared/api/types';

export type AccusationCreateRequest = {
  /** 새 답안은 crypto.randomUUID(), 재채점은 처음 값 그대로 */
  requestId: string;
  /** 이 사건의 인물 (GET map의 suspects) */
  suspectId: number;
  /** 10~3000자 */
  trickExplanation: string;
};

export type AccusationCreateResponse = {
  /** gradingStatus=COMPLETED */
  accusation: AccusationResponse;
  /** 반영 후 횟수. 재전송·상태 조회로 받으면 최초 성공 당시 값이므로 현재 횟수로 쓰지 않는다 */
  accusationCount: UsageCount;
  /** 반영 후 세션 상태. 마찬가지로 화면 전환은 GET state를 다시 조회해 정한다 */
  roomStatus: RoomStatus;
};

export type AccusationListResponse = {
  accusationCount: UsageCount;
  /** 오래된 순 */
  accusations: AccusationResponse[];
};

/** 화면에서 쓰는 지목 입력. AiRequestStatusResponse.input(ACCUSATION)과 같은 모양이다 */
export type AccusationInput = {
  suspectId: number;
  trickExplanation: string;
};

/** 지목 창의 입력 초안. 창을 닫았다 열어도 같은 세션 안에서는 남는다 */
export type AccusationDraft = {
  suspectId: number | null;
  trickExplanation: string;
};
