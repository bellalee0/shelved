import type { AccusationCreateResponse, AccusationResponse } from '../features/accusation/types';
import type { UsageCount } from '../features/room/types';
import { findMockCase } from './data/clocktowerCase';
import {
  currentTimeMs,
  endMockRoom,
  isOutOfTime,
  toServerTime,
  type MockAccusation,
  type MockAccusationRequest,
  type MockAiRequest,
  type MockRoom,
} from './roomDb';

// 지목 기록과 채점 요청 처리를 담는 목 "DB". handlers/accusation·room(state)·aiRequests가 함께 쓴다.
//
// 목의 채점 규칙 (가상 사건 전용. 실제 사건·채점 기준과 관계없다)
// - 목 사건마다 "범인"을 하나 정해 둔다: 사건 1(시계탑 저택) → suspectId 2, 사건 2 → suspectId 11
// - 범인이 틀리면 INCORRECT
// - 범인이 맞으면 트릭 설명에 든 키워드("종소리", "시계") 개수로 판정한다: 둘 다 → CORRECT, 하나 → PARTIAL, 없음 → INCORRECT
// - 정답 → CLEARED, 3번째 완료가 정답이 아니면 → FAILED, 채점 대기 중이던 세션은 정답이 아니면 → EXPIRED
// - 피드백은 판정별 중립 문장이다 (문단 구분 줄바꿈 포함)

/** 목 사건별 범인 (가상 사건) */
const MOCK_CULPRITS: Record<number, number> = { 1: 2, 2: 11 };
/** 목 정답 키워드 (가상 사건) */
const MOCK_KEYWORDS = ['종소리', '시계'];

const MOCK_FEEDBACK = {
  CORRECT: '범인과 수법을 모두 정확히 짚었습니다.\n\n수사를 마무리합니다.',
  PARTIAL:
    '범인을 바르게 짚었습니다. 수법 설명 일부가 맞습니다.\n\n아직 설명하지 못한 부분이 있습니다. 물증과 진술을 다시 살펴보세요.',
  INCORRECT:
    '제출한 답안으로는 사건을 설명하기 어렵습니다.\n\n인물들의 진술과 물증을 다시 비교해 보세요.',
} as const;

/** 채점 실패 후 같은 답안 재채점까지의 최소 간격 (명세 잠정값 10초) */
export const MOCK_GRADING_RETRY_INTERVAL_MS = 10_000;

const GRADING_FAILED_MESSAGE =
  '채점 중 오류가 발생했습니다. 지목 기회는 차감되지 않았으며 같은 답안으로 다시 채점할 수 있습니다.';

function accusationsOf(room: MockRoom): MockAccusation[] {
  room.accusations ??= [];
  return room.accusations;
}

function requestsOf(room: MockRoom): MockAiRequest[] {
  room.aiRequests ??= [];
  return room.aiRequests;
}

export function accusationLimitOf(room: MockRoom): number {
  return findMockCase(room.caseId)?.card.accusationLimit ?? 0;
}

/** 사용 확정(정상 채점 완료 수)과 채점 중 확보를 합쳐 계산한다. 채점 실패는 차감되지 않는다 */
export function accusationCountOf(room: MockRoom): UsageCount {
  const limit = accusationLimitOf(room);
  const accusations = accusationsOf(room);
  const used = accusations.filter((item) => item.gradingStatus === 'COMPLETED').length;
  const reserved = accusations.filter((item) => item.gradingStatus === 'PENDING').length;
  return { limit, used, reserved, remaining: Math.max(0, limit - used - reserved) };
}

export function findMockAccusationByRequestId(
  room: MockRoom,
  requestId: string,
): MockAccusation | undefined {
  return accusationsOf(room).find((item) => item.requestId === requestId);
}

/** 채점 중인 지목 요청 (세션당 한 건) */
export function findProcessingAccusation(room: MockRoom): MockAiRequest | undefined {
  return requestsOf(room).find(
    (request) => request.type === 'ACCUSATION' && request.status === 'PROCESSING',
  );
}

function suspectNameOf(room: MockRoom, suspectId: number): string {
  return (
    findMockCase(room.caseId)?.suspects.find((suspect) => suspect.suspectId === suspectId)?.name ??
    '알 수 없는 인물'
  );
}

export function isMockSuspect(room: MockRoom, suspectId: number): boolean {
  return (
    findMockCase(room.caseId)?.suspects.some((suspect) => suspect.suspectId === suspectId) ?? false
  );
}

export function toAccusationResponse(room: MockRoom, item: MockAccusation): AccusationResponse {
  return {
    accusationId: item.accusationId,
    requestId: item.requestId,
    suspectId: item.suspectId,
    suspectName: suspectNameOf(room, item.suspectId),
    trickExplanation: item.trickExplanation,
    submittedAt: toServerTime(item.submittedAtMs),
    gradingStatus: item.gradingStatus,
    verdict: item.gradingStatus === 'COMPLETED' ? item.verdict : null,
    feedback: item.gradingStatus === 'COMPLETED' ? item.feedback : null,
    attemptNo: item.attemptNo,
    retryAvailableAt:
      item.gradingStatus === 'ERROR' && item.retryAvailableAtMs !== null
        ? toServerTime(item.retryAvailableAtMs)
        : null,
    superseded: item.superseded,
  };
}

/** 지목 기록 (오래된 순) */
export function accusationsFor(room: MockRoom): AccusationResponse[] {
  return [...accusationsOf(room)]
    .sort((a, b) => a.accusationId - b.accusationId)
    .map((item) => toAccusationResponse(room, item));
}

/** GET state의 pendingAccusation: 채점 중이거나 대체되지 않은 채점 실패 지목 (가장 최근 것) */
export function pendingAccusationOf(room: MockRoom): AccusationResponse | null {
  const pending = accusationsOf(room)
    .filter(
      (item) =>
        !item.superseded && (item.gradingStatus === 'PENDING' || item.gradingStatus === 'ERROR'),
    )
    .at(-1);
  return pending ? toAccusationResponse(room, pending) : null;
}

/** 세션 목록 배지(pendingGradingStatus)를 지목 기록에 맞춘다 */
function syncPendingGrading(room: MockRoom) {
  const pending = pendingAccusationOf(room);
  room.pendingGradingStatus =
    pending?.gradingStatus === 'PENDING' || pending?.gradingStatus === 'ERROR'
      ? pending.gradingStatus
      : null;
}

/** 새 답안을 내면 이전의 대체되지 않은 채점 실패 지목은 대체된다 (재채점 불가) */
function supersedeFailed(room: MockRoom) {
  accusationsOf(room).forEach((item) => {
    if (item.gradingStatus === 'ERROR') {
      item.superseded = true;
    }
  });
}

/**
 * 지목을 접수해 채점 중으로 만든다.
 * - 새 답안: 이전 실패 지목을 대체하고 지목·요청을 새로 만든다
 * - 재채점(같은 requestId의 실패 요청): 실행 번호를 올려 같은 지목을 다시 채점한다
 * pollsUntilDone이 null이면 호출한 쪽이 곧바로 finishMockAccusation으로 끝낸다.
 */
export function acceptAccusation(
  room: MockRoom,
  params: {
    requestId: string;
    suspectId: number;
    trickExplanation: string;
    pollsUntilDone: number | null;
    /** true면 첫 실행을 실패로 끝낸다 (재채점은 성공) */
    failFirstAttempt: boolean;
  },
): MockAccusationRequest {
  const nowMs = currentTimeMs();
  const found = requestsOf(room).find((item) => item.requestId === params.requestId);
  let request = found?.type === 'ACCUSATION' ? found : undefined;
  if (!request) {
    supersedeFailed(room);
    request = {
      requestId: params.requestId,
      type: 'ACCUSATION',
      targetId: null,
      status: 'PROCESSING',
      input: { suspectId: params.suspectId, trickExplanation: params.trickExplanation },
      result: null,
      error: null,
      attemptNo: 0,
      pollsUntilDone: null,
      failOnFinish: false,
      acceptedAtMs: nowMs,
      updatedAtMs: nowMs,
      retryAvailableAtMs: null,
    };
    requestsOf(room).push(request);
    const accusations = accusationsOf(room);
    accusations.push({
      accusationId: accusations.reduce((max, item) => Math.max(max, item.accusationId), 0) + 1,
      requestId: params.requestId,
      suspectId: params.suspectId,
      trickExplanation: params.trickExplanation,
      submittedAtMs: nowMs,
      gradingStatus: 'PENDING',
      verdict: null,
      feedback: null,
      attemptNo: null,
      retryAvailableAtMs: null,
      superseded: false,
    });
  }
  const accusation = findMockAccusationByRequestId(room, params.requestId)!;
  accusation.gradingStatus = 'PENDING';
  accusation.retryAvailableAtMs = null;
  request.status = 'PROCESSING';
  request.error = null;
  request.retryAvailableAtMs = null;
  request.attemptNo += 1;
  request.pollsUntilDone = params.pollsUntilDone;
  request.failOnFinish = params.failFirstAttempt && request.attemptNo === 1;
  request.updatedAtMs = nowMs;
  syncPendingGrading(room);
  return request;
}

/** 목 채점 규칙 (파일 맨 위 주석 참고) */
function gradeMock(room: MockRoom, item: MockAccusation): 'CORRECT' | 'PARTIAL' | 'INCORRECT' {
  if (MOCK_CULPRITS[room.caseId] !== item.suspectId) {
    return 'INCORRECT';
  }
  const hits = MOCK_KEYWORDS.filter((keyword) => item.trickExplanation.includes(keyword)).length;
  if (hits === MOCK_KEYWORDS.length) {
    return 'CORRECT';
  }
  return hits > 0 ? 'PARTIAL' : 'INCORRECT';
}

/** 채점 중인 지목을 끝낸다. 성공이면 판정·차감·세션 종료를 한 번만 반영한다 (실패는 차감 없음) */
export function finishMockAccusation(room: MockRoom, request: MockAccusationRequest) {
  const nowMs = currentTimeMs();
  request.pollsUntilDone = null;
  request.updatedAtMs = nowMs;
  const item = findMockAccusationByRequestId(room, request.requestId)!;
  if (request.failOnFinish) {
    request.status = 'FAILED';
    request.error = { code: 'GRADING_FAILED', message: GRADING_FAILED_MESSAGE };
    request.retryAvailableAtMs = nowMs + MOCK_GRADING_RETRY_INTERVAL_MS;
    item.gradingStatus = 'ERROR';
    item.retryAvailableAtMs = request.retryAvailableAtMs;
    syncPendingGrading(room);
    return;
  }
  const verdict = gradeMock(room, item);
  const completedBefore = accusationCountOf(room).used;
  item.gradingStatus = 'COMPLETED';
  item.verdict = verdict;
  item.feedback = MOCK_FEEDBACK[verdict];
  item.attemptNo = completedBefore + 1;

  if (verdict === 'CORRECT') {
    endMockRoom(room, 'CLEARED');
  } else if (item.attemptNo >= accusationLimitOf(room)) {
    endMockRoom(room, 'FAILED');
  } else if (isOutOfTime(room)) {
    // 마감 전에 접수해 채점을 기다리던 지목이 정답이 아니면 시간 만료로 끝난다
    endMockRoom(room, 'EXPIRED');
  }
  syncPendingGrading(room);

  // 최초 성공 당시의 스냅샷이다 (반영 후 횟수·세션 상태 포함)
  const result: AccusationCreateResponse = {
    accusation: toAccusationResponse(room, item),
    accusationCount: accusationCountOf(room),
    roomStatus: room.status,
  };
  request.status = 'SUCCEEDED';
  request.result = result;
}

/** 502 GRADING_FAILED의 detail */
export function gradingFailedDetail(room: MockRoom, request: MockAccusationRequest) {
  const item = findMockAccusationByRequestId(room, request.requestId);
  return {
    requestId: request.requestId,
    accusationId: item?.accusationId ?? null,
    retryAvailableAt:
      request.retryAvailableAtMs != null ? toServerTime(request.retryAvailableAtMs) : null,
  };
}
