import type { RoomTimerResponse } from './types';

/** 서버가 준 타이머와 그 응답을 받은 시각(브라우저 Date.now). 화면 타이머의 기준점이다 */
export type TimerSnapshot = {
  timer: RoomTimerResponse;
  receivedAtMs: number;
};

export function createTimerSnapshot(timer: RoomTimerResponse, receivedAtMs = Date.now()) {
  return { timer, receivedAtMs };
}

/**
 * 화면에 보여 줄 남은 시간. IN_PROGRESS일 때만 받은 시각부터 흐른 만큼 줄이고 0에서 멈춘다.
 * 시간 만료를 확정하지 않는다 — 0이 되면 화면이 서버 상태를 다시 확인한다.
 */
export function computeDisplayRemainingMs(snapshot: TimerSnapshot, nowMs: number): number {
  const { remainingMs, status } = snapshot.timer;
  if (status !== 'IN_PROGRESS') {
    return Math.max(0, remainingMs);
  }
  // 응답을 받기 전 시각으로 계산하는 경우(마지막 화면 갱신 뒤 새 응답이 온 직후)에도 서버 값보다 늘지 않게 한다
  const elapsedMs = Math.max(0, nowMs - snapshot.receivedAtMs);
  return Math.max(0, remainingMs - elapsedMs);
}
