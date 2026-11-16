// 백엔드 hint 도메인 DTO와 같은 이름·필드의 타입 (docs/api/hint-accusation.md)
import type { AiRequestStatusResponse } from '../../shared/api/types';
import type { UsageCount } from '../room/types';

export type HintCreateRequest = {
  /** 새로 보낼 때 crypto.randomUUID(), 재시도는 같은 값 */
  requestId: string;
  /** 1~500자 */
  question: string;
};

/** 보충 질문 요청도 새 힌트와 같은 모양이다 */
export type HintFollowupRequest = HintCreateRequest;

export type HintFollowupResponse = {
  hintId: number;
  requestId: string;
  question: string;
  content: string;
  createdAt: string;
};

export type HintResponse = {
  hintId: number;
  requestId: string;
  question: string;
  content: string;
  createdAt: string;
  followup: HintFollowupResponse | null;
  /** 미사용 기회가 남았는지. 세션 상태와 무관하며 "지금 전송 가능"이라는 뜻이 아니다 */
  followupAvailable: boolean;
};

export type HintCreateResponse = {
  hint: HintResponse;
  /** 최초 성공 당시의 횟수. 현재 횟수로 쓰지 않는다 */
  hintCount: UsageCount;
};

export type HintListResponse = {
  hintCount: UsageCount;
  /** 오래된 순 */
  hints: HintResponse[];
  /** 새 힌트·보충 질문 중 PROCESSING이거나 재시도 대기 중인 FAILED. 없으면 [] */
  pendingRequests: AiRequestStatusResponse[];
};

/** 화면에서 쓰는 새 힌트 질문 입력. AiRequestStatusResponse.input(HINT)과 같은 모양이다 */
export type HintQuestionInput = {
  question: string;
};

/** 화면에서 쓰는 보충 질문 입력. hintId는 상태 객체의 targetId에서 온다 */
export type FollowupQuestionInput = {
  hintId: number;
  question: string;
};
