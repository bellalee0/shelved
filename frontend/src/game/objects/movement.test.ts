import { describe, expect, it } from 'vitest';
import { facingOf, NO_MOVE_INPUT, velocityOf } from './movement';

describe('velocityOf', () => {
  it('대각선 이동은 한 방향 이동과 같은 빠르기로 보정한다', () => {
    // when
    const { vx, vy } = velocityOf({ ...NO_MOVE_INPUT, right: true, down: true }, 100);

    // then
    expect(Math.hypot(vx, vy)).toBeCloseTo(100);
    expect(vx).toBeGreaterThan(0);
    expect(vy).toBeGreaterThan(0);
  });

  it('반대 방향을 함께 누르면 멈춘다', () => {
    // when / then
    expect(velocityOf({ up: true, down: true, left: true, right: true }, 100)).toEqual({
      vx: 0,
      vy: 0,
    });
  });
});

describe('facingOf', () => {
  it('좌우 이동을 우선하고, 멈추면 직전 방향을 유지한다', () => {
    // when / then
    expect(facingOf(-1, 1, 'down')).toBe('left');
    expect(facingOf(0, -1, 'down')).toBe('up');
    expect(facingOf(0, 0, 'right')).toBe('right');
  });
});
