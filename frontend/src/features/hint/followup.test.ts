import { describe, expect, it } from 'vitest';
import type { RoomStatus } from '../../shared/api/types';
import { canSendFollowup, followupBlockReason, type FollowupConditions } from './followup';

const OK: FollowupConditions = {
  followupAvailable: true,
  timerStatus: 'IN_PROGRESS',
  remainingMs: 60_000,
  hasConnection: true,
};

describe('보충 질문 버튼 활성화 조건', () => {
  const cases: [string, Partial<FollowupConditions>, ReturnType<typeof followupBlockReason>][] = [
    ['모두 만족하면 보낼 수 있다', {}, null],
    ['followupAvailable이 false면 보낼 수 없다', { followupAvailable: false }, 'UNAVAILABLE'],
    [
      '일시정지면 followupAvailable이 true여도 보낼 수 없다',
      { timerStatus: 'PAUSED' },
      'NOT_IN_PROGRESS',
    ],
    ['시작 전이면 보낼 수 없다', { timerStatus: 'READY' }, 'NOT_IN_PROGRESS'],
    ['종료 상태면 보낼 수 없다', { timerStatus: 'CLEARED' }, 'NOT_IN_PROGRESS'],
    ['상태를 아직 모르면 보낼 수 없다', { timerStatus: null }, 'NOT_IN_PROGRESS'],
    ['남은 시간이 0이면 보낼 수 없다', { remainingMs: 0 }, 'TIME_OVER'],
    ['남은 시간을 모르면 보낼 수 없다', { remainingMs: null }, 'TIME_OVER'],
    ['남은 시간이 1ms라도 있으면 보낼 수 있다', { remainingMs: 1 }, null],
    ['조작 연결이 없으면(연결 교체 포함) 보낼 수 없다', { hasConnection: false }, 'NO_CONNECTION'],
    [
      '여러 조건이 안 맞으면 세션 상태 이유를 먼저 알려 준다',
      { followupAvailable: false, timerStatus: 'PAUSED', hasConnection: false },
      'NOT_IN_PROGRESS',
    ],
  ];

  it.each(cases)('%s', (_name, override, expected) => {
    const conditions = { ...OK, ...override };
    expect(followupBlockReason(conditions)).toBe(expected);
    expect(canSendFollowup(conditions)).toBe(expected === null);
  });

  it('IN_PROGRESS 외의 모든 상태에서 보낼 수 없다', () => {
    const statuses: RoomStatus[] = ['READY', 'PAUSED', 'CLEARED', 'FAILED', 'EXPIRED'];
    statuses.forEach((timerStatus) => {
      expect(canSendFollowup({ ...OK, timerStatus })).toBe(false);
    });
  });
});
