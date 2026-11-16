import { describe, expect, it } from 'vitest';
import type { AiRequestStatusResponse } from '../../shared/api/types';
import {
  attachFollowup,
  formatHintUsage,
  mergeHint,
  pickPendingRequest,
  readFollowupQuestionInput,
  readFollowupResult,
  readHintResult,
  withPendingReservation,
} from './hints';
import type { HintFollowupResponse, HintResponse } from './types';

function hint(hintId: number, overrides: Partial<HintResponse> = {}): HintResponse {
  return {
    hintId,
    requestId: `req-${hintId}`,
    question: `질문 ${hintId}`,
    content: `답변 ${hintId}`,
    createdAt: '2026-11-16T10:00:00',
    followup: null,
    followupAvailable: true,
    ...overrides,
  };
}

function pending(
  overrides: Partial<AiRequestStatusResponse> & Pick<AiRequestStatusResponse, 'requestId'>,
): AiRequestStatusResponse {
  return {
    type: 'HINT',
    targetId: null,
    status: 'PROCESSING',
    input: { question: '질문' },
    result: null,
    error: null,
    retryAvailableAt: null,
    acceptedAt: '2026-11-16T10:00:00',
    updatedAt: '2026-11-16T10:00:00',
    ...overrides,
  };
}

describe('mergeHint', () => {
  it('같은 hintId는 한 번만 두고 hintId 순으로 정렬한다', () => {
    const merged = mergeHint([hint(2), hint(1)], hint(3));
    expect(merged.map((item) => item.hintId)).toEqual([1, 2, 3]);
    expect(mergeHint(merged, hint(2, { content: '다른 내용' }))).toBe(merged);
  });
});

describe('attachFollowup', () => {
  const followup: HintFollowupResponse = {
    hintId: 1,
    requestId: 'f-1',
    question: '보충',
    content: '설명',
    createdAt: '2026-11-16T10:01:00',
  };

  it('해당 힌트에 붙이고 followupAvailable을 false로 바꾼다', () => {
    const result = attachFollowup([hint(1), hint(2)], 1, followup);
    expect(result[0].followup).toEqual(followup);
    expect(result[0].followupAvailable).toBe(false);
    expect(result[1].followup).toBeNull();
  });

  it('이미 붙어 있으면 그대로 둔다 (중복 방지)', () => {
    const attached = attachFollowup([hint(1)], 1, followup);
    expect(attachFollowup(attached, 1, { ...followup, content: '다른 설명' })[0].followup).toEqual(
      followup,
    );
  });
});

describe('pickPendingRequest', () => {
  it('유형별로 처리 중인 요청을 먼저 고른다', () => {
    const list = [
      pending({ requestId: 'a', status: 'FAILED' }),
      pending({ requestId: 'b', status: 'PROCESSING' }),
      pending({ requestId: 'c', type: 'HINT_FOLLOWUP', targetId: 1 }),
    ];
    expect(pickPendingRequest(list, 'HINT')?.requestId).toBe('b');
    expect(pickPendingRequest(list, 'HINT_FOLLOWUP')?.requestId).toBe('c');
  });

  it('처리 중이 없으면 가장 최근 실패 요청을 고른다', () => {
    const list = [
      pending({ requestId: 'old', status: 'FAILED', acceptedAt: '2026-11-16T10:00:00' }),
      pending({ requestId: 'new', status: 'FAILED', acceptedAt: '2026-11-16T10:05:00' }),
    ];
    expect(pickPendingRequest(list, 'HINT')?.requestId).toBe('new');
  });

  it('알 수 없는 유형(지목 등)은 무시한다', () => {
    const list = [
      pending({
        requestId: 'x',
        type: 'ACCUSATION',
        input: { suspectId: 1, trickExplanation: '설명' },
      }),
    ];
    expect(pickPendingRequest(list, 'HINT')).toBeNull();
    expect(pickPendingRequest(list, 'HINT_FOLLOWUP')).toBeNull();
  });
});

describe('입력·결과 읽기', () => {
  it('보충 질문 입력은 targetId를 hintId로 쓴다', () => {
    expect(
      readFollowupQuestionInput(pending({ requestId: 'f', type: 'HINT_FOLLOWUP', targetId: 4 })),
    ).toEqual({ hintId: 4, question: '질문' });
    expect(
      readFollowupQuestionInput(pending({ requestId: 'f', type: 'HINT_FOLLOWUP', targetId: null })),
    ).toBeNull();
  });

  it('형식이 다른 결과는 null이다', () => {
    expect(readHintResult({ foo: 1 })).toBeNull();
    expect(readFollowupResult(null)).toBeNull();
    expect(
      readHintResult({
        hint: hint(1),
        hintCount: { limit: 3, used: 1, reserved: 0, remaining: 2 },
      }),
    ).not.toBeNull();
  });
});

describe('힌트 횟수 표시', () => {
  it('처리 중이 아니면 서버 값을 그대로 쓴다', () => {
    const count = { limit: 3, used: 1, reserved: 0, remaining: 2 };
    expect(withPendingReservation(count, false)).toBe(count);
  });

  it('처리 중이면 한 건을 처리 중으로 센다. 서버가 이미 세고 있으면 두 번 세지 않는다', () => {
    expect(withPendingReservation({ limit: 3, used: 1, reserved: 0, remaining: 2 }, true)).toEqual({
      limit: 3,
      used: 1,
      reserved: 1,
      remaining: 1,
    });
    const counted = { limit: 3, used: 1, reserved: 1, remaining: 1 };
    expect(withPendingReservation(counted, true)).toBe(counted);
  });

  it('사용 완료와 처리 중을 구분해 표시한다', () => {
    expect(formatHintUsage({ limit: 5, used: 2, reserved: 1, remaining: 2 })).toBe(
      '사용 2회 · 처리 중 1회 · 남은 2회',
    );
    expect(formatHintUsage({ limit: 5, used: 2, reserved: 0, remaining: 3 })).toBe(
      '사용 2회 · 남은 3회',
    );
  });
});
