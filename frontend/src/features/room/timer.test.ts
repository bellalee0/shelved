import { describe, expect, it } from 'vitest';
import { computeDisplayRemainingMs, createTimerSnapshot } from './timer';
import type { RoomTimerResponse } from './types';

function timerOf(override: Partial<RoomTimerResponse> = {}): RoomTimerResponse {
  return {
    status: 'IN_PROGRESS',
    timeLimitSec: 1200,
    elapsedActiveMs: 0,
    remainingMs: 10_000,
    awaitingGrading: false,
    serverTime: '2026-11-06T10:00:00',
    ...override,
  };
}

describe('computeDisplayRemainingMs', () => {
  it('진행 중이면 받은 시각부터 흐른 만큼 줄인다', () => {
    // given
    const snapshot = createTimerSnapshot(timerOf({ remainingMs: 10_000 }), 1_000);

    // when
    const result = computeDisplayRemainingMs(snapshot, 4_500);

    // then
    expect(result).toBe(6_500);
  });

  it('0에서 멈추고 음수가 되지 않는다', () => {
    // given
    const snapshot = createTimerSnapshot(timerOf({ remainingMs: 2_000 }), 0);

    // when
    const result = computeDisplayRemainingMs(snapshot, 60_000);

    // then
    expect(result).toBe(0);
  });

  it('새 서버 값을 받으면 그 값으로 다시 맞춘다', () => {
    // given 화면은 5초가 지나 5000ms를 보여 주던 중
    const old = createTimerSnapshot(timerOf({ remainingMs: 10_000 }), 0);
    expect(computeDisplayRemainingMs(old, 5_000)).toBe(5_000);

    // when 서버는 아직 8000ms가 남았다고 알려 준다
    const fresh = createTimerSnapshot(timerOf({ remainingMs: 8_000 }), 5_000);

    // then
    expect(computeDisplayRemainingMs(fresh, 5_000)).toBe(8_000);
    expect(computeDisplayRemainingMs(fresh, 6_000)).toBe(7_000);
  });

  it('응답을 받기 전 시각으로 계산해도 서버 값보다 늘지 않는다', () => {
    // given
    const snapshot = createTimerSnapshot(timerOf({ remainingMs: 8_000 }), 5_000);

    // when
    const result = computeDisplayRemainingMs(snapshot, 4_000);

    // then
    expect(result).toBe(8_000);
  });

  it('진행 중이 아니면 시간이 지나도 서버 값을 그대로 보여 준다', () => {
    // given
    const snapshot = createTimerSnapshot(timerOf({ status: 'PAUSED', remainingMs: 768_750 }), 0);

    // when
    const result = computeDisplayRemainingMs(snapshot, 100_000);

    // then
    expect(result).toBe(768_750);
  });
});
