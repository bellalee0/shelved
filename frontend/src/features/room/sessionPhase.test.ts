import { describe, expect, it } from 'vitest';
import { decideSessionPhase } from './sessionPhase';
import type { RoomTimerResponse } from './types';

function timerOf(override: Partial<RoomTimerResponse>): RoomTimerResponse {
  return {
    status: 'IN_PROGRESS',
    timeLimitSec: 1200,
    elapsedActiveMs: 0,
    remainingMs: 600_000,
    awaitingGrading: false,
    serverTime: '2026-11-06T10:00:00',
    ...override,
  };
}

describe('decideSessionPhase', () => {
  it.each<[string, Partial<RoomTimerResponse>, boolean, string]>([
    ['종료 상태면 결과 화면', { status: 'CLEARED' }, true, 'ended'],
    [
      '채점 대기면 채점 대기 화면',
      { status: 'IN_PROGRESS', awaitingGrading: true },
      true,
      'awaitingGrading',
    ],
    ['READY면 수사 준비', { status: 'READY' }, false, 'ready'],
    ['PAUSED이고 시간이 남았으면 이어하기', { status: 'PAUSED' }, false, 'paused'],
    [
      'PAUSED인데 남은 시간이 없으면 채점 대기',
      { status: 'PAUSED', remainingMs: 0 },
      false,
      'awaitingGrading',
    ],
    ['진행 중이고 연결이 있으면 진행', { status: 'IN_PROGRESS' }, true, 'playing'],
    [
      '진행 중인데 연결이 없으면 다른 화면에서 진행 중',
      { status: 'IN_PROGRESS' },
      false,
      'replaced',
    ],
  ])('%s', (_, override, hasConnection, expected) => {
    // when
    const phase = decideSessionPhase(timerOf(override), hasConnection);

    // then
    expect(phase).toBe(expected);
  });
});
