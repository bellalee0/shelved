import { describe, expect, it } from 'vitest';
import {
  decideSessionPhase,
  holdAwaitingGrading,
  keepsConnection,
  type SessionPhase,
} from './sessionPhase';
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

describe('keepsConnection', () => {
  it.each<[string, SessionPhase, Partial<RoomTimerResponse>, boolean]>([
    ['진행', 'playing', {}, true],
    ['유예 구간의 채점 대기', 'awaitingGrading', { remainingMs: 0 }, true],
    ['시간이 확정된 채점 대기', 'awaitingGrading', { status: 'PAUSED', remainingMs: 0 }, false],
    ['일시정지', 'paused', { status: 'PAUSED' }, false],
    ['종료', 'ended', { status: 'EXPIRED', remainingMs: 0 }, false],
    ['다른 화면에서 진행', 'replaced', {}, false],
  ])('%s', (_, phase, override, expected) => {
    expect(keepsConnection(phase, timerOf(override))).toBe(expected);
  });
});

describe('holdAwaitingGrading', () => {
  it.each<[string, SessionPhase, SessionPhase, number, SessionPhase]>([
    ['채점 대기 뒤 남은 시간 0이면 유지', 'awaitingGrading', 'playing', 0, 'awaitingGrading'],
    ['남은 시간이 있으면 진행', 'awaitingGrading', 'playing', 1000, 'playing'],
    ['채점 대기가 아니었으면 그대로', 'playing', 'playing', 0, 'playing'],
    ['종료는 그대로', 'awaitingGrading', 'ended', 0, 'ended'],
  ])('%s', (_, prev, next, remainingMs, expected) => {
    expect(holdAwaitingGrading(prev, next, timerOf({ remainingMs }))).toBe(expected);
  });
});
