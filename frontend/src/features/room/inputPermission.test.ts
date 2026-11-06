import { describe, expect, it } from 'vitest';
import { canMoveOnMap, canOperateSession, type InputConditions } from './inputPermission';

const ALLOWED: InputConditions = {
  mapReady: true,
  hasConnection: true,
  connectionReplaced: false,
  hasOpenWindow: false,
  hasOverlay: false,
  status: 'IN_PROGRESS',
  displayRemainingMs: 60_000,
  awaitingGrading: false,
};

describe('canMoveOnMap', () => {
  it('모든 조건을 만족하면 이동을 허용한다', () => {
    // given
    const conditions = ALLOWED;

    // when
    const result = canMoveOnMap(conditions);

    // then
    expect(result).toBe(true);
  });

  it.each<[string, Partial<InputConditions>]>([
    ['MAP_READY 전', { mapReady: false }],
    ['연결 없음', { hasConnection: false }],
    ['연결 교체됨', { connectionReplaced: true }],
    ['열린 창 있음', { hasOpenWindow: true }],
    ['오버레이 표시 중', { hasOverlay: true }],
    ['READY', { status: 'READY' }],
    ['PAUSED', { status: 'PAUSED' }],
    ['종료 상태', { status: 'EXPIRED' }],
    ['상태 모름', { status: null }],
    ['화면 남은 시간 0', { displayRemainingMs: 0 }],
    ['화면 남은 시간 모름', { displayRemainingMs: null }],
    ['채점 대기', { awaitingGrading: true }],
  ])('%s이면 이동을 막는다', (_, override) => {
    // given
    const conditions = { ...ALLOWED, ...override };

    // when
    const result = canMoveOnMap(conditions);

    // then
    expect(result).toBe(false);
  });
});

describe('canOperateSession', () => {
  it('노트 창 등 창이 열려 있거나 맵이 준비 전이어도 세션 조작(노트 저장)은 허용한다', () => {
    // given
    const conditions = { ...ALLOWED, hasOpenWindow: true, mapReady: false };

    // when
    const result = canOperateSession(conditions);

    // then
    expect(result).toBe(true);
    expect(canMoveOnMap(conditions)).toBe(false);
  });

  it.each<[string, Partial<InputConditions>]>([
    ['연결 없음', { hasConnection: false }],
    ['연결 교체됨', { connectionReplaced: true }],
    ['오버레이 표시 중', { hasOverlay: true }],
    ['PAUSED', { status: 'PAUSED' }],
    ['화면 남은 시간 0', { displayRemainingMs: 0 }],
    ['채점 대기', { awaitingGrading: true }],
  ])('%s이면 세션 조작을 막는다', (_, override) => {
    // given
    const conditions = { ...ALLOWED, ...override };

    // when
    const result = canOperateSession(conditions);

    // then
    expect(result).toBe(false);
  });
});
