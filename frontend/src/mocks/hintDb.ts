import type {
  HintCreateResponse,
  HintFollowupResponse,
  HintResponse,
} from '../features/hint/types';
import type { UsageCount } from '../features/room/types';
import { findMockCase } from './data/clocktowerCase';
import {
  currentTimeMs,
  toServerTime,
  type MockAiRequest,
  type MockHint,
  type MockQuestionRequest,
  type MockRoom,
} from './roomDb';

// 힌트 기록과 힌트·보충 질문 요청 처리를 담는 목 "DB". handlers/hint·room(state)이 함께 쓴다.
// 실제 AI 대신 정해진 중립 문장으로 답한다. 가상 사건용이며 정답·인물의 내부 설정과 관련이 없다.

const MOCK_HINT_ANSWERS = [
  '지금까지 확인한 물증과 인물의 진술을 시간순으로 나란히 놓고 비교해 보세요.',
  '진술이 서로 어긋나는 부분이 있는지, 그 어긋남을 확인할 물증이 있는지 살펴보세요.',
  '아직 조사하지 않은 장소나 물증이 없는지 맵을 다시 둘러보세요.',
];
const MOCK_FOLLOWUP_ANSWERS = [
  '앞의 힌트는 이미 확인한 내용을 다시 정리해 보라는 뜻입니다. 새로운 단서를 더한 것은 아닙니다.',
  '말씀하신 부분은 앞의 힌트가 가리킨 범위 안에서 다시 풀어 설명할 수 있습니다.',
];

const HINT_GENERATION_FAILED_MESSAGE = '힌트를 만들지 못했습니다. 같은 질문을 다시 보내 주세요.';
const HINT_FOLLOWUP_FAILED_MESSAGE =
  '보충 질문의 답변을 받지 못했습니다. 같은 질문을 다시 보내 주세요.';

function hintsOf(room: MockRoom): MockHint[] {
  room.hints ??= [];
  return room.hints;
}

function requestsOf(room: MockRoom): MockAiRequest[] {
  room.aiRequests ??= [];
  return room.aiRequests;
}

export function hintLimitOf(room: MockRoom): number {
  return findMockCase(room.caseId)?.card.hintLimit ?? 0;
}

/** 사용 확정(저장된 힌트 수)과 처리 중 확보를 합쳐 계산한다. 실패한 요청은 차감되지 않는다 */
export function hintCountOf(room: MockRoom): UsageCount {
  const limit = hintLimitOf(room);
  const used = hintsOf(room).length;
  const reserved = requestsOf(room).filter(
    (request) => request.type === 'HINT' && request.status === 'PROCESSING',
  ).length;
  return { limit, used, reserved, remaining: Math.max(0, limit - used - reserved) };
}

export function findMockHint(room: MockRoom, hintId: number): MockHint | undefined {
  return hintsOf(room).find((hint) => hint.hintId === hintId);
}

/** 처리 중인 새 힌트 (세션당 한 건) */
export function findProcessingHint(room: MockRoom): MockAiRequest | undefined {
  return requestsOf(room).find(
    (request) => request.type === 'HINT' && request.status === 'PROCESSING',
  );
}

/** 이 힌트에 처리 중인 보충 질문 (힌트당 한 건) */
export function findProcessingFollowup(room: MockRoom, hintId: number): MockAiRequest | undefined {
  return requestsOf(room).find(
    (request) =>
      request.type === 'HINT_FOLLOWUP' &&
      request.targetId === hintId &&
      request.status === 'PROCESSING',
  );
}

function toFollowupResponse(hint: MockHint): HintFollowupResponse | null {
  if (!hint.followup) {
    return null;
  }
  return {
    // 보충 질문이 달린 힌트의 ID
    hintId: hint.hintId,
    requestId: hint.followup.requestId,
    question: hint.followup.question,
    content: hint.followup.content,
    createdAt: toServerTime(hint.followup.createdAtMs),
  };
}

export function toHintResponse(room: MockRoom, hint: MockHint): HintResponse {
  return {
    hintId: hint.hintId,
    requestId: hint.requestId,
    question: hint.question,
    content: hint.content,
    createdAt: toServerTime(hint.createdAtMs),
    followup: toFollowupResponse(hint),
    // 세션 상태와 무관: 미사용 기회가 남았고 처리 중인 보충 질문이 없다
    followupAvailable: hint.followup === null && !findProcessingFollowup(room, hint.hintId),
  };
}

/** 힌트 기록 (오래된 순) */
export function hintsFor(room: MockRoom): HintResponse[] {
  return [...hintsOf(room)]
    .sort((a, b) => a.hintId - b.hintId)
    .map((hint) => toHintResponse(room, hint));
}

/**
 * 새 힌트 또는 보충 질문을 접수해 처리 중으로 만든다. 같은 requestId의 실패 요청이면 실행 번호를 올려 다시 실행한다.
 * pollsUntilDone이 null이면 호출한 쪽이 곧바로 finishMockAiRequest로 끝낸다.
 */
export function acceptHintRequest(
  room: MockRoom,
  params: {
    requestId: string;
    type: 'HINT' | 'HINT_FOLLOWUP';
    /** HINT_FOLLOWUP이면 대상 힌트의 ID */
    targetId: number | null;
    question: string;
    pollsUntilDone: number | null;
    /** true면 첫 실행을 실패로 끝낸다 (재시도는 성공) */
    failFirstAttempt: boolean;
  },
): MockQuestionRequest {
  const nowMs = currentTimeMs();
  const found = requestsOf(room).find((item) => item.requestId === params.requestId);
  let request = found && found.type !== 'ACCUSATION' ? found : undefined;
  if (!request) {
    request = {
      requestId: params.requestId,
      type: params.type,
      targetId: params.targetId,
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
    requestsOf(room).push(request);
  }
  request.status = 'PROCESSING';
  request.error = null;
  request.attemptNo += 1;
  request.pollsUntilDone = params.pollsUntilDone;
  request.failOnFinish = params.failFirstAttempt && request.attemptNo === 1;
  request.updatedAtMs = nowMs;
  return request;
}

/** 처리 중인 힌트·보충 질문 요청을 끝낸다. 성공이면 기록에 한 번만 저장한다 (실패는 차감·사용 처리 없음) */
export function finishMockHintRequest(room: MockRoom, request: MockQuestionRequest) {
  const nowMs = currentTimeMs();
  request.pollsUntilDone = null;
  request.updatedAtMs = nowMs;
  const isHint = request.type === 'HINT';
  if (request.failOnFinish) {
    request.status = 'FAILED';
    request.error = isHint
      ? { code: 'HINT_GENERATION_FAILED', message: HINT_GENERATION_FAILED_MESSAGE }
      : { code: 'HINT_FOLLOWUP_FAILED', message: HINT_FOLLOWUP_FAILED_MESSAGE };
    return;
  }
  const hints = hintsOf(room);
  if (isHint) {
    const hint: MockHint = {
      hintId: hints.reduce((max, item) => Math.max(max, item.hintId), 0) + 1,
      requestId: request.requestId,
      question: request.input.question,
      content: MOCK_HINT_ANSWERS[hints.length % MOCK_HINT_ANSWERS.length],
      createdAtMs: nowMs,
      followup: null,
    };
    hints.push(hint);
    request.status = 'SUCCEEDED';
    // 최초 성공 당시의 스냅샷이다 (사용 반영 후 횟수 포함)
    const result: HintCreateResponse = {
      hint: toHintResponse(room, hint),
      hintCount: hintCountOf(room),
    };
    request.result = result;
    return;
  }
  const hint = hints.find((item) => item.hintId === request.targetId);
  if (!hint) {
    request.status = 'FAILED';
    request.error = { code: 'HINT_NOT_FOUND', message: '힌트를 찾을 수 없습니다.' };
    return;
  }
  hint.followup = {
    requestId: request.requestId,
    question: request.input.question,
    content: MOCK_FOLLOWUP_ANSWERS[hints.indexOf(hint) % MOCK_FOLLOWUP_ANSWERS.length],
    createdAtMs: nowMs,
  };
  request.status = 'SUCCEEDED';
  request.result = toFollowupResponse(hint);
}
