import type { AiRequestError } from '../../shared/api/aiRequestController';
import type {
  AccusationResponse,
  AiRequestStatusResponse,
  RoomStatus,
} from '../../shared/api/types';
import {
  TRICK_EXPLANATION_MAX_LENGTH,
  TRICK_EXPLANATION_MIN_LENGTH,
} from '../../shared/constants/accusation';
import type { UsageCount } from '../room/types';
import type { AccusationCreateResponse, AccusationInput } from './types';

// 지목을 다루는 순수 함수 (화면·API와 무관하게 테스트한다).
// 정답 기준·항목별 판정은 서버가 내려주지 않으므로 여기서도 추측하지 않는다. 판정은 응답의 verdict만 쓴다.

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

const GRADING_STATUSES = ['PENDING', 'COMPLETED', 'ERROR'];
const ENDED_STATUSES: RoomStatus[] = ['CLEARED', 'FAILED', 'EXPIRED'];

export function isEndedRoomStatus(status: RoomStatus | null): boolean {
  return status !== null && ENDED_STATUSES.includes(status);
}

function isAccusation(value: unknown): value is AccusationResponse {
  return (
    isRecord(value) &&
    typeof value.accusationId === 'number' &&
    typeof value.requestId === 'string' &&
    typeof value.suspectId === 'number' &&
    typeof value.trickExplanation === 'string' &&
    typeof value.gradingStatus === 'string' &&
    GRADING_STATUSES.includes(value.gradingStatus)
  );
}

/** 지목 채점의 완료 결과인지 확인한다 (POST 200 응답, 상태 조회의 result) */
export function isAccusationCreateResponse(value: unknown): value is AccusationCreateResponse {
  return (
    isRecord(value) &&
    isAccusation(value.accusation) &&
    isRecord(value.accusationCount) &&
    typeof value.roomStatus === 'string'
  );
}

export function readAccusationResult(result: unknown): AccusationCreateResponse | null {
  return isAccusationCreateResponse(result) ? result : null;
}

/** 상태 객체의 input에서 지목 입력을 꺼낸다 (재채점은 이 입력을 그대로 다시 보낸다) */
export function readAccusationInput(status: AiRequestStatusResponse): AccusationInput | null {
  const input: unknown = status.input;
  return isRecord(input) &&
    typeof input.suspectId === 'number' &&
    typeof input.trickExplanation === 'string'
    ? { suspectId: input.suspectId, trickExplanation: input.trickExplanation }
    : null;
}

export function isSameAccusationInput(a: AccusationInput, b: AccusationInput): boolean {
  return a.suspectId === b.suspectId && a.trickExplanation === b.trickExplanation;
}

/** 서버가 이 requestId를 채점 실패로 기록해 둔 경우. 이때만 재채점 형식(연결 헤더 없음)으로 다시 보낸다 */
const GRADING_RETRY_CODES = new Set(['GRADING_FAILED', 'GRADING_RETRY_TOO_SOON']);

/**
 * 실패한 지목을 같은 requestId로 다시 보낼 때, 재채점(연결 헤더 없음)인지 정한다.
 * 서버가 요청을 받지 못했던 경우(AI_REQUEST_NOT_FOUND·응답 형식 오류 등)는 새 답안과 같은 형식으로 보낸다.
 */
export function isGradingRetry(previousError: AiRequestError | null): boolean {
  return previousError?.code != null && GRADING_RETRY_CODES.has(previousError.code);
}

/** 화면에 다시 표시할 지목 요청. 처리 중이 있으면 그것, 없으면 가장 최근에 접수된 실패 요청이다 */
export function pickAccusationRequest(
  aiRequests: AiRequestStatusResponse[],
): AiRequestStatusResponse | null {
  const candidates = aiRequests.filter((request) => request.type === 'ACCUSATION');
  const processing = candidates.find((request) => request.status === 'PROCESSING');
  if (processing) {
    return processing;
  }
  return candidates
    .filter((request) => request.status === 'FAILED')
    .reduce<AiRequestStatusResponse | null>(
      (latest, request) =>
        latest === null || request.acceptedAt >= latest.acceptedAt ? request : latest,
      null,
    );
}

/**
 * 재접속·상태 재조회 때 공통 훅에 복원시킬 상태 객체.
 * 폴링·재채점 입력의 기준은 aiRequests다(requestId·input·retryAvailableAt이 모두 있다).
 * aiRequests에 없는데 pendingAccusation만 있는 경우를 대비해 그 값으로 같은 모양을 만든다.
 */
export function toRestorableAccusation(
  aiRequests: AiRequestStatusResponse[],
  pendingAccusation: AccusationResponse | null,
): AiRequestStatusResponse | null {
  const fromRequests = pickAccusationRequest(aiRequests);
  if (fromRequests) {
    return fromRequests;
  }
  if (
    pendingAccusation === null ||
    pendingAccusation.superseded ||
    pendingAccusation.gradingStatus === 'COMPLETED'
  ) {
    return null;
  }
  const failed = pendingAccusation.gradingStatus === 'ERROR';
  return {
    requestId: pendingAccusation.requestId,
    type: 'ACCUSATION',
    targetId: null,
    status: failed ? 'FAILED' : 'PROCESSING',
    input: {
      suspectId: pendingAccusation.suspectId,
      trickExplanation: pendingAccusation.trickExplanation,
    },
    result: null,
    error: failed ? { code: 'GRADING_FAILED', message: '' } : null,
    retryAvailableAt: pendingAccusation.retryAvailableAt,
    acceptedAt: pendingAccusation.submittedAt,
    updatedAt: pendingAccusation.submittedAt,
  };
}

/** 지목을 기록에 합친다. 같은 accusationId는 새 값으로 바꾸고 accusationId 순(접수 순)으로 정렬한다 */
export function mergeAccusation(
  accusations: AccusationResponse[],
  added: AccusationResponse,
): AccusationResponse[] {
  return [...accusations.filter((item) => item.accusationId !== added.accusationId), added].sort(
    (a, b) => a.accusationId - b.accusationId,
  );
}

/**
 * 채점 중이면 그 몫을 "처리 중"으로 미리 센다 (받은 횟수가 접수 이전 값일 수 있다).
 * 서버가 이미 처리 중으로 세고 있으면(reserved ≥ 1) 두 번 세지 않는다.
 */
export function withGradingReservation(count: UsageCount, grading: boolean): UsageCount {
  if (!grading || count.reserved > 0) {
    return count;
  }
  return { ...count, reserved: 1, remaining: Math.max(0, count.remaining - 1) };
}

/** 트릭 설명 글자 수. 앞뒤 공백은 보내지 않으므로 세지 않는다 */
export function countTrickLength(text: string): number {
  return text.trim().length;
}

// ---- 새 답안 제출 조건 ----

export type AccusationSubmitConditions = {
  /** GET state의 timer.status */
  timerStatus: RoomStatus | null;
  /** 화면 타이머의 남은 시간 */
  remainingMs: number | null;
  awaitingGrading: boolean;
  /** 이 화면이 현재 조작 연결을 갖고 있는지 (연결 교체면 false) */
  hasConnection: boolean;
  /** 남은 지목 기회 (처리 중 확보 반영). 모르면 null */
  remaining: number | null;
  /** 채점 중인 지목이 있는지 (이 화면의 요청 또는 서버의 PENDING) */
  gradingInProgress: boolean;
  suspectId: number | null;
  trickExplanation: string;
  /** 채점에 실패한 답안과 같은 입력인지. 같으면 새 답안이 아니라 재채점이다 */
  sameAsFailedInput: boolean;
};

export type AccusationSubmitBlockReason =
  | 'NOT_IN_PROGRESS'
  | 'TIME_OVER'
  | 'NO_CONNECTION'
  | 'NO_CHANCES'
  | 'GRADING_IN_PROGRESS'
  | 'NO_SUSPECT'
  | 'TOO_SHORT'
  | 'TOO_LONG'
  | 'SAME_AS_FAILED';

export const ACCUSATION_SUBMIT_BLOCK_MESSAGES: Record<AccusationSubmitBlockReason, string> = {
  NOT_IN_PROGRESS: '수사가 진행 중일 때만 지목할 수 있습니다.',
  TIME_OVER: '제한시간이 끝나 새 답안을 제출할 수 없습니다.',
  NO_CONNECTION: '이 화면의 수사 연결이 없습니다. 수사를 이어서 진행한 뒤 제출해 주세요.',
  NO_CHANCES: '남은 지목 기회가 없습니다.',
  GRADING_IN_PROGRESS: '채점 중인 지목이 있습니다. 결과가 나온 뒤 제출할 수 있습니다.',
  NO_SUSPECT: '범인으로 지목할 인물을 골라 주세요.',
  TOO_SHORT: `트릭 설명을 ${TRICK_EXPLANATION_MIN_LENGTH}자 이상 입력해 주세요.`,
  TOO_LONG: `트릭 설명은 ${TRICK_EXPLANATION_MAX_LENGTH}자까지 입력할 수 있습니다.`,
  SAME_AS_FAILED:
    '채점에 실패한 답안과 같습니다. 위의 "같은 답안 다시 채점"을 눌러 주세요. 답안을 고치면 새로 제출할 수 있습니다.',
};

/**
 * 새 답안을 지금 제출할 수 없는 이유. 제출할 수 있으면 null.
 * 세션 조건(진행 중·남은 시간·현재 연결·남은 기회·채점 중 없음)을 먼저 보고, 그다음 입력을 본다.
 * 서버도 같은 조건을 다시 검사하며, 이 함수는 버튼 활성화와 안내 문구용이다.
 */
export function accusationSubmitBlockReason(
  conditions: AccusationSubmitConditions,
): AccusationSubmitBlockReason | null {
  if (conditions.timerStatus !== 'IN_PROGRESS') {
    return 'NOT_IN_PROGRESS';
  }
  if (
    conditions.awaitingGrading ||
    conditions.remainingMs === null ||
    conditions.remainingMs <= 0
  ) {
    return 'TIME_OVER';
  }
  if (!conditions.hasConnection) {
    return 'NO_CONNECTION';
  }
  if (conditions.gradingInProgress) {
    return 'GRADING_IN_PROGRESS';
  }
  if (conditions.remaining === null || conditions.remaining <= 0) {
    return 'NO_CHANCES';
  }
  if (conditions.suspectId === null) {
    return 'NO_SUSPECT';
  }
  const length = countTrickLength(conditions.trickExplanation);
  if (length < TRICK_EXPLANATION_MIN_LENGTH) {
    return 'TOO_SHORT';
  }
  if (length > TRICK_EXPLANATION_MAX_LENGTH) {
    return 'TOO_LONG';
  }
  if (conditions.sameAsFailedInput) {
    return 'SAME_AS_FAILED';
  }
  return null;
}

/** 마감 후(남은 시간 0·채점 대기)인지. 이때는 새 답안 입력 대신 재채점만 보여 준다 */
export function isPastDeadline(remainingMs: number | null, awaitingGrading: boolean): boolean {
  return awaitingGrading || remainingMs === 0;
}

// ---- 같은 답안 재채점 조건 ----

/** 서버 시각 문자열(오프셋 없는 ISO-8601)을 ms로. 읽을 수 없으면 null */
export function parseServerTimeMs(value: string | null): number | null {
  if (value === null) {
    return null;
  }
  const ms = Date.parse(value);
  return Number.isNaN(ms) ? null : ms;
}

export type GradingRetryConditions = {
  /** 이 지목이 채점 실패(ERROR) 상태인지 */
  failed: boolean;
  superseded: boolean;
  timerStatus: RoomStatus | null;
  /** 재채점 가능 서버 시각. 모르면 null (서버가 429로 알려 준다) */
  retryAvailableAt: string | null;
  /** 지금의 서버 시각 추정값 (브라우저 시각 + 서버 시각 차이) */
  serverNowMs: number;
};

export type GradingRetryBlockReason = 'NOT_FAILED' | 'SUPERSEDED' | 'ENDED' | 'TOO_SOON';

/**
 * 같은 답안 재채점을 지금 보낼 수 없는 이유. 보낼 수 있으면 null.
 * 채점 실패 + 대체되지 않음 + 세션 종료 전 + 최소 간격(retryAvailableAt) 경과. 일시정지·마감 후에도 가능하다.
 */
export function gradingRetryBlockReason(
  conditions: GradingRetryConditions,
): GradingRetryBlockReason | null {
  if (!conditions.failed) {
    return 'NOT_FAILED';
  }
  if (conditions.superseded) {
    return 'SUPERSEDED';
  }
  if (isEndedRoomStatus(conditions.timerStatus)) {
    return 'ENDED';
  }
  if (secondsUntilRetry(conditions.retryAvailableAt, conditions.serverNowMs) > 0) {
    return 'TOO_SOON';
  }
  return null;
}

/** 재채점까지 남은 초 (올림). 시각을 모르거나 지났으면 0 */
export function secondsUntilRetry(retryAvailableAt: string | null, serverNowMs: number): number {
  const availableMs = parseServerTimeMs(retryAvailableAt);
  if (availableMs === null) {
    return 0;
  }
  return Math.max(0, Math.ceil((availableMs - serverNowMs) / 1000));
}
