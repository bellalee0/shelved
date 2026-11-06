import type { Direction } from '../assets';

// 키 입력 → 속도·바라보는 방향. Phaser에 의존하지 않는 순수 함수.

export type MoveInput = { up: boolean; down: boolean; left: boolean; right: boolean };

export const NO_MOVE_INPUT: MoveInput = { up: false, down: false, left: false, right: false };

/** 누른 방향키로 속도를 구한다. 대각선은 1/√2를 곱해 한 방향 이동과 같은 빠르기로 맞춘다 */
export function velocityOf(input: MoveInput, speed: number) {
  const dx = Number(input.right) - Number(input.left);
  const dy = Number(input.down) - Number(input.up);
  if (dx === 0 && dy === 0) {
    return { vx: 0, vy: 0 };
  }
  const scale = dx !== 0 && dy !== 0 ? speed / Math.SQRT2 : speed;
  return { vx: dx * scale, vy: dy * scale };
}

/** 이동 중 바라보는 방향. 좌우 입력을 우선하고, 멈추면 직전 방향을 유지한다 */
export function facingOf(vx: number, vy: number, previous: Direction): Direction {
  if (vx < 0) return 'left';
  if (vx > 0) return 'right';
  if (vy < 0) return 'up';
  if (vy > 0) return 'down';
  return previous;
}
