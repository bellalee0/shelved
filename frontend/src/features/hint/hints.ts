import type { AiRequestStatusResponse } from '../../shared/api/types';
import type { UsageCount } from '../room/types';
import type {
  FollowupQuestionInput,
  HintCreateResponse,
  HintFollowupResponse,
  HintListResponse,
  HintQuestionInput,
  HintResponse,
} from './types';

// 힌트 기록을 다루는 순수 함수 (화면·API와 무관하게 테스트한다)

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

function isHintFollowup(value: unknown): value is HintFollowupResponse {
  return (
    isRecord(value) &&
    typeof value.hintId === 'number' &&
    typeof value.requestId === 'string' &&
    typeof value.question === 'string' &&
    typeof value.content === 'string'
  );
}

function isHint(value: unknown): value is HintResponse {
  return (
    isRecord(value) &&
    typeof value.hintId === 'number' &&
    typeof value.requestId === 'string' &&
    typeof value.question === 'string' &&
    typeof value.content === 'string' &&
    (value.followup === null || isHintFollowup(value.followup))
  );
}

/** 새 힌트의 완료 결과인지 확인한다 (POST 200 응답, 상태 조회의 result) */
export function isHintCreateResponse(value: unknown): value is HintCreateResponse {
  return isRecord(value) && isHint(value.hint);
}

/** 보충 질문의 완료 결과인지 확인한다 */
export function isHintFollowupResponse(value: unknown): value is HintFollowupResponse {
  return isHintFollowup(value);
}

export function readHintResult(result: unknown): HintCreateResponse | null {
  return isHintCreateResponse(result) ? result : null;
}

export function readFollowupResult(result: unknown): HintFollowupResponse | null {
  return isHintFollowupResponse(result) ? result : null;
}

/** 상태 객체의 input에서 새 힌트 질문을 꺼낸다 (재시도는 이 질문을 그대로 다시 보낸다) */
export function readHintQuestionInput(status: AiRequestStatusResponse): HintQuestionInput | null {
  const input: unknown = status.input;
  return isRecord(input) && typeof input.question === 'string'
    ? { question: input.question }
    : null;
}

/** 상태 객체에서 보충 질문 입력을 꺼낸다. 대상 힌트는 targetId에 있다 */
export function readFollowupQuestionInput(
  status: AiRequestStatusResponse,
): FollowupQuestionInput | null {
  const input: unknown = status.input;
  return status.targetId !== null && isRecord(input) && typeof input.question === 'string'
    ? { hintId: status.targetId, question: input.question }
    : null;
}

/**
 * 대화창에 다시 표시할 요청 하나를 고른다 (type별로 하나). 처리 중인 요청이 있으면 그것,
 * 없으면 가장 최근에 접수된 실패 요청이다 (재시도 대상은 마지막 실패 하나로 단순화).
 * 다른 유형(NPC_ASK·ACCUSATION)은 각자의 창이 복원한다.
 */
export function pickPendingRequest(
  pendingRequests: AiRequestStatusResponse[],
  type: 'HINT' | 'HINT_FOLLOWUP',
): AiRequestStatusResponse | null {
  const candidates = pendingRequests.filter((request) => request.type === type);
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

/** 새 힌트를 기록에 더한다. 같은 hintId는 한 번만 두고 hintId 순(시간순)으로 정렬한다 */
export function mergeHint(hints: HintResponse[], added: HintResponse): HintResponse[] {
  if (hints.some((hint) => hint.hintId === added.hintId)) {
    return hints;
  }
  return [...hints, added].sort((a, b) => a.hintId - b.hintId);
}

/** 보충 질문·답변을 해당 힌트에 붙인다. 이미 붙어 있으면 그대로 둔다 */
export function attachFollowup(
  hints: HintResponse[],
  hintId: number,
  followup: HintFollowupResponse,
): HintResponse[] {
  return hints.map((hint) =>
    hint.hintId === hintId && hint.followup === null
      ? { ...hint, followup, followupAvailable: false }
      : hint,
  );
}

/** 완료 결과를 기록에 즉시 표시하면서 처리 중 요청 목록에서는 뺀다. hintCount는 건드리지 않는다 */
export function removePending(list: HintListResponse, requestId: string): HintListResponse {
  return {
    ...list,
    pendingRequests: list.pendingRequests.filter((pending) => pending.requestId !== requestId),
  };
}

/**
 * 새 힌트가 처리 중이면 그 몫을 "처리 중"으로 미리 센다 (GET으로 받은 횟수가 접수 이전 값일 수 있다).
 * 서버가 이미 처리 중으로 세고 있으면(reserved ≥ 1) 두 번 세지 않는다.
 */
export function withPendingReservation(count: UsageCount, hintProcessing: boolean): UsageCount {
  if (!hintProcessing || count.reserved > 0) {
    return count;
  }
  return { ...count, reserved: 1, remaining: Math.max(0, count.remaining - 1) };
}

/** "사용 2회 · 처리 중 1회 · 남은 N회" (처리 중이 없으면 그 부분은 생략) */
export function formatHintUsage(count: UsageCount): string {
  const reserved = count.reserved > 0 ? ` · 처리 중 ${count.reserved}회` : '';
  return `사용 ${count.used}회${reserved} · 남은 ${count.remaining}회`;
}
