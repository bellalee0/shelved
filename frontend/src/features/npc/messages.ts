import type { AiRequestStatusResponse } from '../../shared/api/types';
import type { NpcAskResponse, NpcMessageResponse, NpcQuestionInput } from './types';

// NPC 대화 기록을 다루는 순수 함수 (화면·API와 무관하게 테스트한다)

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isNpcMessage(value: unknown): value is NpcMessageResponse {
  return (
    isRecord(value) &&
    typeof value.messageId === 'number' &&
    (value.senderType === 'USER' || value.senderType === 'NPC') &&
    typeof value.content === 'string' &&
    typeof value.sequenceNo === 'number'
  );
}

/** NPC 질문의 완료 결과인지 확인한다 (POST 200 응답, 상태 조회의 result) */
export function isNpcAskResponse(value: unknown): value is NpcAskResponse {
  return (
    isRecord(value) &&
    typeof value.requestId === 'string' &&
    isNpcMessage(value.question) &&
    isNpcMessage(value.answer)
  );
}

/** 상태 조회의 result를 완료 결과로 읽는다. 형식이 다르면 null (대화는 GET messages로 다시 받는다) */
export function readNpcAskResult(result: unknown): NpcAskResponse | null {
  return isNpcAskResponse(result) ? result : null;
}

/** 상태 객체의 input에서 질문을 꺼낸다 (재시도는 이 질문을 그대로 다시 보낸다) */
export function readNpcQuestionInput(status: AiRequestStatusResponse): NpcQuestionInput | null {
  const input: unknown = status.input;
  return isRecord(input) && typeof input.question === 'string'
    ? { question: input.question }
    : null;
}

/**
 * 대화 기록에 메시지를 더한다. 같은 messageId는 한 번만 두고 sequenceNo 순으로 정렬한다.
 * 완료 결과를 즉시 보여 준 뒤 GET messages로 다시 받아도 말풍선이 겹치지 않게 한다.
 */
export function mergeNpcMessages(
  messages: NpcMessageResponse[],
  added: NpcMessageResponse[],
): NpcMessageResponse[] {
  const byId = new Map(messages.map((message) => [message.messageId, message]));
  added.forEach((message) => {
    if (!byId.has(message.messageId)) {
      byId.set(message.messageId, message);
    }
  });
  return [...byId.values()].sort((a, b) => a.sequenceNo - b.sequenceNo);
}

/**
 * 대화창에 다시 표시할 요청 하나를 고른다. 처리 중인 요청이 있으면 그것(NPC별 한 개),
 * 없으면 가장 최근에 접수된 실패 요청이다 (재시도 대상은 마지막 실패 하나로 단순화).
 */
export function pickPendingRequest(
  pendingRequests: AiRequestStatusResponse[],
): AiRequestStatusResponse | null {
  const processing = pendingRequests.find((request) => request.status === 'PROCESSING');
  if (processing) {
    return processing;
  }
  const failed = pendingRequests.filter((request) => request.status === 'FAILED');
  return failed.reduce<AiRequestStatusResponse | null>(
    (latest, request) =>
      latest === null || request.acceptedAt >= latest.acceptedAt ? request : latest,
    null,
  );
}

export type ConversationSummaryItem = {
  suspectId: number;
  messageCount: number;
  lastMessageAt: string;
};

export type ConversationListItem = ConversationSummaryItem & {
  /** 맵 정보의 표시 이름 */
  name: string;
};

/**
 * 세션 상태의 conversations와 맵의 인물 이름을 suspectId로 잇는다. 최근 대화한 인물이 위로 온다.
 * 맵에서 이름을 찾지 못하면 '인물'로 표시한다.
 */
export function joinConversations(
  conversations: ConversationSummaryItem[],
  suspects: { suspectId: number; name: string }[],
): ConversationListItem[] {
  const names = new Map(suspects.map((suspect) => [suspect.suspectId, suspect.name]));
  return conversations
    .map((conversation) => ({
      ...conversation,
      name: names.get(conversation.suspectId) ?? '인물',
    }))
    .sort((a, b) => b.lastMessageAt.localeCompare(a.lastMessageAt) || a.suspectId - b.suspectId);
}
