import type { ConversationSummary } from '../features/room/types';
import type { NpcAskResponse, NpcMessageResponse } from '../features/npc/types';
import type { AiRequestStatusResponse } from '../shared/api/types';
import {
  currentTimeMs,
  toServerTime,
  type MockAiRequest,
  type MockNpcMessage,
  type MockQuestionRequest,
  type MockRoom,
} from './roomDb';
import { finishMockHintRequest } from './hintDb';
import { finishMockAccusation } from './accusationDb';

// NPC 대화 기록과 AI 요청 상태를 담는 목 "DB". handlers/npc·aiRequests·room(state)이 함께 쓴다.
// 실제 AI 대신 정해진 중립 문장으로 답한다. 가상 사건용이며 인물의 내부 설정·정답과 관련이 없다.

/** 목 NPC 답변. 어떤 인물·질문에도 쓸 수 있는 중립적인 문장만 둔다 */
const MOCK_NPC_ANSWERS = [
  '글쎄요, 그 일이라면 저도 자세히는 모릅니다.',
  '그때는 제 일을 하느라 바빴습니다. 다른 분께도 물어보시지요.',
  '기억나는 대로 말씀드리자면, 특별히 이상한 점은 느끼지 못했습니다.',
  '확실하지 않은 이야기는 하고 싶지 않군요.',
];

const NPC_ANSWER_FAILED_MESSAGE = 'NPC의 답변을 받지 못했습니다. 같은 질문을 다시 보내 주세요.';

function npcMessagesOf(room: MockRoom): MockNpcMessage[] {
  room.npcMessages ??= [];
  return room.npcMessages;
}

function aiRequestsOf(room: MockRoom): MockAiRequest[] {
  room.aiRequests ??= [];
  return room.aiRequests;
}

export function findMockAiRequest(room: MockRoom, requestId: string): MockAiRequest | undefined {
  return aiRequestsOf(room).find((request) => request.requestId === requestId);
}

/** 이 NPC에게 처리 중인 질문 (NPC별 한 개) */
export function findProcessingNpcAsk(room: MockRoom, suspectId: number): MockAiRequest | undefined {
  return aiRequestsOf(room).find(
    (request) =>
      request.type === 'NPC_ASK' &&
      request.targetId === suspectId &&
      request.status === 'PROCESSING',
  );
}

export function toAiRequestStatus(request: MockAiRequest): AiRequestStatusResponse {
  return {
    requestId: request.requestId,
    type: request.type,
    targetId: request.targetId,
    status: request.status,
    input: request.input,
    result: request.status === 'SUCCEEDED' ? request.result : null,
    error: request.status === 'FAILED' ? request.error : null,
    retryAvailableAt:
      request.status === 'FAILED' && request.retryAvailableAtMs != null
        ? toServerTime(request.retryAvailableAtMs)
        : null,
    acceptedAt: toServerTime(request.acceptedAtMs),
    updatedAt: toServerTime(request.updatedAtMs),
  };
}

/**
 * 처리 중이거나 재시도 대기 중인 실패 요청 (GET state의 aiRequests, GET messages·hints의 pendingRequests).
 * 재시도 대기는 같은 대상(유형 + targetId)의 가장 최근 요청이 FAILED인 경우다. 이후 다른 요청을 보냈다면 이전 실패는 나오지 않는다.
 * suspectId를 주면 그 NPC의 질문만, types를 주면 그 유형만 돌려준다.
 */
export function pendingAiRequestsOf(
  room: MockRoom,
  filter: { suspectId?: number; types?: MockAiRequest['type'][] } = {},
): AiRequestStatusResponse[] {
  const requests = aiRequestsOf(room);
  return requests
    .filter((request, index) => {
      if (filter.suspectId !== undefined) {
        if (request.type !== 'NPC_ASK' || request.targetId !== filter.suspectId) {
          return false;
        }
      }
      if (filter.types !== undefined && !filter.types.includes(request.type)) {
        return false;
      }
      if (request.status === 'PROCESSING') {
        return true;
      }
      return (
        request.status === 'FAILED' &&
        !requests.some(
          (other, otherIndex) =>
            otherIndex > index &&
            other.type === request.type &&
            other.targetId === request.targetId,
        )
      );
    })
    .map(toAiRequestStatus);
}

function toNpcMessageResponse(message: MockNpcMessage): NpcMessageResponse {
  return {
    messageId: message.messageId,
    senderType: message.senderType,
    content: message.content,
    sequenceNo: message.sequenceNo,
    createdAt: toServerTime(message.createdAtMs),
  };
}

/** 한 NPC와의 대화 원문 (sequenceNo 순) */
export function npcMessagesFor(room: MockRoom, suspectId: number): NpcMessageResponse[] {
  return npcMessagesOf(room)
    .filter((message) => message.suspectId === suspectId)
    .sort((a, b) => a.sequenceNo - b.sequenceNo)
    .map(toNpcMessageResponse);
}

/** 대화한 NPC 목록 (GET state의 conversations) */
export function conversationsOf(room: MockRoom): ConversationSummary[] {
  const bySuspect = new Map<number, ConversationSummary & { lastMs: number }>();
  npcMessagesOf(room).forEach((message) => {
    const current = bySuspect.get(message.suspectId);
    bySuspect.set(message.suspectId, {
      suspectId: message.suspectId,
      messageCount: (current?.messageCount ?? 0) + 1,
      lastMs: Math.max(current?.lastMs ?? 0, message.createdAtMs),
      lastMessageAt: '',
    });
  });
  return [...bySuspect.values()].map(({ lastMs, ...summary }) => ({
    ...summary,
    lastMessageAt: toServerTime(lastMs),
  }));
}

/**
 * 질문을 접수해 처리 중으로 만든다. 같은 requestId의 실패 요청이면 실행 번호를 올려 다시 실행한다.
 * pollsUntilDone이 null이면 호출한 쪽이 곧바로 finishMockAiRequest로 끝낸다.
 */
export function acceptNpcAsk(
  room: MockRoom,
  params: {
    requestId: string;
    suspectId: number;
    question: string;
    pollsUntilDone: number | null;
    /** true면 첫 실행을 실패로 끝낸다 (재시도는 성공) */
    failFirstAttempt: boolean;
  },
): MockQuestionRequest {
  const nowMs = currentTimeMs();
  const found = findMockAiRequest(room, params.requestId);
  let request = found?.type === 'NPC_ASK' ? found : undefined;
  if (!request) {
    request = {
      requestId: params.requestId,
      type: 'NPC_ASK',
      targetId: params.suspectId,
      status: 'PROCESSING',
      input: { question: params.question },
      result: null,
      error: null,
      attemptNo: 0,
      pollsUntilDone: null,
      failOnFinish: false,
      acceptedAtMs: nowMs,
      updatedAtMs: nowMs,
    };
    aiRequestsOf(room).push(request);
  }
  request.status = 'PROCESSING';
  request.error = null;
  request.attemptNo += 1;
  request.pollsUntilDone = params.pollsUntilDone;
  request.failOnFinish = params.failFirstAttempt && request.attemptNo === 1;
  request.updatedAtMs = nowMs;
  return request;
}

/** 처리 중인 요청을 끝낸다. 성공이면 질문·답변을 대화 기록에 한 번만 저장한다 */
export function finishMockAiRequest(room: MockRoom, request: MockAiRequest) {
  if (request.type === 'ACCUSATION') {
    finishMockAccusation(room, request);
    return;
  }
  if (request.type !== 'NPC_ASK' || request.targetId === null) {
    finishMockHintRequest(room, request);
    return;
  }
  const nowMs = currentTimeMs();
  request.pollsUntilDone = null;
  request.updatedAtMs = nowMs;
  if (request.failOnFinish) {
    request.status = 'FAILED';
    request.error = { code: 'NPC_ANSWER_FAILED', message: NPC_ANSWER_FAILED_MESSAGE };
    return;
  }
  const messages = npcMessagesOf(room);
  const suspectMessages = messages.filter((message) => message.suspectId === request.targetId);
  const nextMessageId = messages.reduce((max, message) => Math.max(max, message.messageId), 0) + 1;
  const question: MockNpcMessage = {
    messageId: nextMessageId,
    suspectId: request.targetId,
    senderType: 'USER',
    content: request.input.question,
    sequenceNo: suspectMessages.length + 1,
    createdAtMs: request.acceptedAtMs,
  };
  const answer: MockNpcMessage = {
    messageId: nextMessageId + 1,
    suspectId: request.targetId,
    senderType: 'NPC',
    content: MOCK_NPC_ANSWERS[(suspectMessages.length / 2) % MOCK_NPC_ANSWERS.length],
    sequenceNo: suspectMessages.length + 2,
    createdAtMs: nowMs,
  };
  messages.push(question, answer);
  const result: NpcAskResponse = {
    requestId: request.requestId,
    suspectId: request.targetId,
    question: toNpcMessageResponse(question),
    answer: toNpcMessageResponse(answer),
  };
  request.status = 'SUCCEEDED';
  request.result = result;
}

/** 상태 조회가 올 때마다 부른다. 처리 중 요청의 남은 조회 횟수를 줄이고 0이 되면 끝낸다 */
export function advanceMockAiRequest(room: MockRoom, request: MockAiRequest) {
  if (request.status !== 'PROCESSING' || request.pollsUntilDone === null) {
    return;
  }
  request.pollsUntilDone -= 1;
  if (request.pollsUntilDone <= 0) {
    finishMockAiRequest(room, request);
  }
}
