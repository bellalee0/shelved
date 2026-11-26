import { describe, expect, it } from 'vitest';
import type { AccusationResponse, AiRequestStatusResponse } from '../../shared/api/types';
import {
  accusationSubmitBlockReason,
  gradingRetryBlockReason,
  isGradingRetry,
  isPastDeadline,
  mergeAccusation,
  pickAccusationRequest,
  readAccusationInput,
  secondsUntilRetry,
  toRestorableAccusation,
  withGradingReservation,
  type AccusationSubmitConditions,
  type GradingRetryConditions,
} from './accusations';

const VALID_TRICK = '가상의 범인이 시계를 미리 돌려 두었다.';

const SUBMITTABLE: AccusationSubmitConditions = {
  timerStatus: 'IN_PROGRESS',
  remainingMs: 600_000,
  awaitingGrading: false,
  hasConnection: true,
  remaining: 2,
  gradingInProgress: false,
  suspectId: 2,
  trickExplanation: VALID_TRICK,
  sameAsFailedInput: false,
};

function accusation(overrides: Partial<AccusationResponse> = {}): AccusationResponse {
  return {
    accusationId: 1,
    requestId: 'request-1',
    suspectId: 2,
    suspectName: '마거릿',
    trickExplanation: VALID_TRICK,
    submittedAt: '2026-11-26T10:00:00',
    gradingStatus: 'COMPLETED',
    verdict: 'PARTIAL',
    feedback: '피드백',
    attemptNo: 1,
    retryAvailableAt: null,
    superseded: false,
    ...overrides,
  };
}

function statusOf(overrides: Partial<AiRequestStatusResponse> = {}): AiRequestStatusResponse {
  return {
    requestId: 'request-1',
    type: 'ACCUSATION',
    targetId: null,
    status: 'PROCESSING',
    input: { suspectId: 2, trickExplanation: VALID_TRICK },
    result: null,
    error: null,
    retryAvailableAt: null,
    acceptedAt: '2026-11-26T10:00:00',
    updatedAt: '2026-11-26T10:00:00',
    ...overrides,
  };
}

describe('accusationSubmitBlockReason (새 답안 제출 조건)', () => {
  it('모든 조건을 만족하면 제출할 수 있다', () => {
    expect(accusationSubmitBlockReason(SUBMITTABLE)).toBeNull();
  });

  it.each<[string, Partial<AccusationSubmitConditions>, string]>([
    ['일시정지', { timerStatus: 'PAUSED' }, 'NOT_IN_PROGRESS'],
    ['시작 전', { timerStatus: 'READY' }, 'NOT_IN_PROGRESS'],
    ['종료', { timerStatus: 'CLEARED' }, 'NOT_IN_PROGRESS'],
    ['상태를 모름', { timerStatus: null }, 'NOT_IN_PROGRESS'],
    ['남은 시간 0', { remainingMs: 0 }, 'TIME_OVER'],
    ['남은 시간을 모름', { remainingMs: null }, 'TIME_OVER'],
    ['채점 대기', { awaitingGrading: true }, 'TIME_OVER'],
    ['연결 없음(교체 포함)', { hasConnection: false }, 'NO_CONNECTION'],
    ['채점 중인 지목 있음', { gradingInProgress: true }, 'GRADING_IN_PROGRESS'],
    ['남은 기회 0', { remaining: 0 }, 'NO_CHANCES'],
    ['횟수를 모름', { remaining: null }, 'NO_CHANCES'],
    ['인물 미선택', { suspectId: null }, 'NO_SUSPECT'],
    ['9자', { trickExplanation: '가'.repeat(9) }, 'TOO_SHORT'],
    ['공백만 길다', { trickExplanation: `  ${'가'.repeat(9)}      ` }, 'TOO_SHORT'],
    ['3001자', { trickExplanation: '가'.repeat(3001) }, 'TOO_LONG'],
    ['실패한 답안과 같음', { sameAsFailedInput: true }, 'SAME_AS_FAILED'],
  ])('%s이면 제출할 수 없다', (_name, overrides, expected) => {
    // given
    const conditions = { ...SUBMITTABLE, ...overrides };

    // when
    const reason = accusationSubmitBlockReason(conditions);

    // then
    expect(reason).toBe(expected);
  });

  it.each([10, 3000])('트릭 설명 %i자는 경계 안이라 제출할 수 있다', (length) => {
    expect(
      accusationSubmitBlockReason({ ...SUBMITTABLE, trickExplanation: '가'.repeat(length) }),
    ).toBeNull();
  });

  it('채점 중이면 처리 중 확보로 남은 기회가 0이어도 "채점 중"을 먼저 알려 준다', () => {
    expect(
      accusationSubmitBlockReason({ ...SUBMITTABLE, gradingInProgress: true, remaining: 0 }),
    ).toBe('GRADING_IN_PROGRESS');
  });
});

describe('gradingRetryBlockReason (같은 답안 재채점 조건)', () => {
  const NOW = Date.parse('2026-11-26T10:00:20');
  const RETRYABLE: GradingRetryConditions = {
    failed: true,
    superseded: false,
    timerStatus: 'IN_PROGRESS',
    retryAvailableAt: '2026-11-26T10:00:10',
    serverNowMs: NOW,
  };

  it('채점 실패 + 대체 아님 + 최소 간격 경과 + 종료 전이면 재채점할 수 있다', () => {
    expect(gradingRetryBlockReason(RETRYABLE)).toBeNull();
  });

  it.each<[string, Partial<GradingRetryConditions>, string | null]>([
    ['채점 실패가 아님', { failed: false }, 'NOT_FAILED'],
    ['새 답안으로 대체됨', { superseded: true }, 'SUPERSEDED'],
    ['해결로 종료', { timerStatus: 'CLEARED' }, 'ENDED'],
    ['시간 만료로 종료', { timerStatus: 'EXPIRED' }, 'ENDED'],
    ['최소 간격 전', { retryAvailableAt: '2026-11-26T10:00:25' }, 'TOO_SOON'],
    ['일시정지(마감 후 포함)', { timerStatus: 'PAUSED' }, null],
    ['재채점 가능 시각을 모름', { retryAvailableAt: null }, null],
    ['정확히 가능 시각', { retryAvailableAt: '2026-11-26T10:00:20' }, null],
  ])('%s → %s', (_name, overrides, expected) => {
    expect(gradingRetryBlockReason({ ...RETRYABLE, ...overrides })).toBe(expected);
  });

  it('남은 초는 올림하고 지났으면 0이다', () => {
    expect(secondsUntilRetry('2026-11-26T10:00:25', NOW - 500)).toBe(6);
    expect(secondsUntilRetry('2026-11-26T10:00:10', NOW)).toBe(0);
    expect(secondsUntilRetry(null, NOW)).toBe(0);
    expect(secondsUntilRetry('잘못된 값', NOW)).toBe(0);
  });
});

describe('isGradingRetry', () => {
  it('서버가 채점 실패로 기록한 요청만 재채점 형식(연결 헤더 없음)으로 다시 보낸다', () => {
    expect(isGradingRetry({ code: 'GRADING_FAILED', message: '' })).toBe(true);
    expect(isGradingRetry({ code: 'GRADING_RETRY_TOO_SOON', message: '' })).toBe(true);
    expect(isGradingRetry({ code: 'AI_REQUEST_NOT_FOUND', message: '' })).toBe(false);
    expect(isGradingRetry({ code: null, message: '' })).toBe(false);
    expect(isGradingRetry(null)).toBe(false);
  });
});

describe('isPastDeadline', () => {
  it('남은 시간이 0이거나 채점 대기면 마감 후다', () => {
    expect(isPastDeadline(0, false)).toBe(true);
    expect(isPastDeadline(1000, true)).toBe(true);
    expect(isPastDeadline(1000, false)).toBe(false);
    expect(isPastDeadline(null, false)).toBe(false);
  });
});

describe('지목 복원', () => {
  it('처리 중인 지목을 실패한 지목보다 먼저 고르고, 다른 유형은 무시한다', () => {
    // given
    const failed = statusOf({ requestId: 'failed', status: 'FAILED' });
    const processing = statusOf({ requestId: 'processing' });
    const hint = statusOf({ requestId: 'hint', type: 'HINT', input: { question: '질문' } });

    // when / then
    expect(pickAccusationRequest([hint, failed, processing])?.requestId).toBe('processing');
    expect(pickAccusationRequest([hint, failed])?.requestId).toBe('failed');
    expect(pickAccusationRequest([hint])).toBeNull();
  });

  it('aiRequests를 기준으로 하고, 없으면 pendingAccusation으로 같은 모양을 만든다', () => {
    // given
    const fromRequests = statusOf({ requestId: 'from-requests', status: 'FAILED' });
    const pending = accusation({
      requestId: 'from-pending',
      gradingStatus: 'ERROR',
      verdict: null,
      feedback: null,
      attemptNo: null,
      retryAvailableAt: '2026-11-26T10:00:10',
    });

    // when
    const preferred = toRestorableAccusation([fromRequests], pending);
    const fallback = toRestorableAccusation([], pending);

    // then
    expect(preferred?.requestId).toBe('from-requests');
    expect(fallback).toMatchObject({
      requestId: 'from-pending',
      type: 'ACCUSATION',
      status: 'FAILED',
      retryAvailableAt: '2026-11-26T10:00:10',
      error: { code: 'GRADING_FAILED' },
    });
    expect(readAccusationInput(fallback!)).toEqual({ suspectId: 2, trickExplanation: VALID_TRICK });
  });

  it('대체된 지목·완료된 지목은 복원하지 않는다', () => {
    expect(
      toRestorableAccusation([], accusation({ gradingStatus: 'ERROR', superseded: true })),
    ).toBeNull();
    expect(toRestorableAccusation([], accusation())).toBeNull();
    expect(toRestorableAccusation([], null)).toBeNull();
  });
});

describe('mergeAccusation', () => {
  it('같은 accusationId는 한 번만 두고 새 값으로 바꾸며 접수 순으로 정렬한다', () => {
    // given
    const first = accusation({ accusationId: 1 });
    const pending = accusation({ accusationId: 2, gradingStatus: 'PENDING', verdict: null });
    const completed = {
      ...pending,
      gradingStatus: 'COMPLETED' as const,
      verdict: 'CORRECT' as const,
    };

    // when
    const merged = mergeAccusation([pending, first], completed);

    // then
    expect(merged.map((item) => item.accusationId)).toEqual([1, 2]);
    expect(merged[1].verdict).toBe('CORRECT');
  });
});

describe('withGradingReservation', () => {
  it('채점 중이면 처리 중 1회를 미리 세고, 서버가 이미 셌으면 두 번 세지 않는다', () => {
    const count = { limit: 3, used: 1, reserved: 0, remaining: 2 };
    expect(withGradingReservation(count, true)).toEqual({ ...count, reserved: 1, remaining: 1 });
    expect(withGradingReservation(count, false)).toBe(count);
    const reserved = { limit: 3, used: 1, reserved: 1, remaining: 1 };
    expect(withGradingReservation(reserved, true)).toBe(reserved);
  });
});
