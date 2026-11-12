// 백엔드 npc 도메인 DTO와 같은 이름·필드의 타입 (docs/api/investigation.md)
import type { AiRequestStatusResponse, MessageSenderType } from '../../shared/api/types';

export type NpcAskRequest = {
  /** 새로 보낼 때 crypto.randomUUID(), 재시도는 같은 값 */
  requestId: string;
  /** 1~500자 */
  question: string;
};

export type NpcMessageResponse = {
  messageId: number;
  senderType: MessageSenderType;
  content: string;
  /** 이 NPC 대화 안의 순번 (정렬 기준) */
  sequenceNo: number;
  createdAt: string;
};

export type NpcAskResponse = {
  requestId: string;
  suspectId: number;
  /** 저장된 플레이어 질문 */
  question: NpcMessageResponse;
  /** 저장된 NPC 답변 */
  answer: NpcMessageResponse;
};

export type NpcMessageListResponse = {
  suspectId: number;
  suspectName: string;
  /** sequenceNo 순 */
  messages: NpcMessageResponse[];
  /** 이 NPC에게 보낸 질문 중 PROCESSING이거나 재시도 대기 중인 FAILED. 없으면 [] */
  pendingRequests: AiRequestStatusResponse[];
};

/** 화면에서 쓰는 질문 입력. AiRequestStatusResponse.input(NPC_ASK)과 같은 모양이다 */
export type NpcQuestionInput = {
  question: string;
};
