import { describe, expect, it } from 'vitest';
import type { RoomMapEvidence, RoomMapSuspect } from '../features/room/types';
import { findNearestTarget, listInteractionTargets } from './interaction';

const suspect = (overrides: Partial<RoomMapSuspect>): RoomMapSuspect => ({
  suspectId: 1,
  name: '인물',
  spriteKey: 'npc-maid-01',
  canConverse: true,
  x: 100,
  y: 100,
  ...overrides,
});

const evidence = (overrides: Partial<RoomMapEvidence>): RoomMapEvidence => ({
  evidenceId: 10,
  name: '물증',
  x: 200,
  y: 200,
  ...overrides,
});

const RADIUS = 48;

describe('listInteractionTargets', () => {
  it('대화 불가 인물과 미배치 인물은 대상에서 빼고 물증은 모두 넣는다', () => {
    // given
    const suspects = [
      suspect({ suspectId: 1 }),
      suspect({ suspectId: 2, canConverse: false }),
      suspect({ suspectId: 3, x: null, y: null, spriteKey: null }),
    ];
    const evidences = [evidence({ evidenceId: 10 }), evidence({ evidenceId: 11 })];

    // when
    const targets = listInteractionTargets(suspects, evidences);

    // then
    expect(targets.map(({ type, targetId }) => `${type}:${targetId}`)).toEqual([
      'SUSPECT:1',
      'EVIDENCE:10',
      'EVIDENCE:11',
    ]);
  });
});

describe('findNearestTarget', () => {
  it('반경 안의 대상 중 가장 가까운 하나를 고른다', () => {
    // given
    const targets = listInteractionTargets(
      [suspect({ suspectId: 1, x: 130, y: 100 })],
      [evidence({ evidenceId: 10, x: 110, y: 100 })],
    );

    // when
    const target = findNearestTarget({ x: 100, y: 100 }, targets, RADIUS);

    // then
    expect(target).toMatchObject({ type: 'EVIDENCE', targetId: 10 });
  });

  it('반경 밖의 대상은 고르지 않는다 (반경 경계는 포함)', () => {
    // given
    const targets = listInteractionTargets(
      [suspect({ suspectId: 1, x: 100 + RADIUS, y: 100 })],
      [evidence({ evidenceId: 10, x: 100, y: 100 + RADIUS + 1 })],
    );

    // when
    const target = findNearestTarget({ x: 100, y: 100 }, targets, RADIUS);

    // then
    expect(target).toMatchObject({ type: 'SUSPECT', targetId: 1 });
  });

  it('가까이 있어도 대화 불가 인물은 고르지 않는다', () => {
    // given
    const targets = listInteractionTargets(
      [suspect({ suspectId: 2, canConverse: false, x: 101, y: 100 })],
      [],
    );

    // when
    const target = findNearestTarget({ x: 100, y: 100 }, targets, RADIUS);

    // then
    expect(target).toBeNull();
  });

  it('반경 안에 대상이 없으면 null', () => {
    // given
    const targets = listInteractionTargets([suspect({ x: 400, y: 400 })], [evidence({})]);

    // when
    const target = findNearestTarget({ x: 0, y: 0 }, targets, RADIUS);

    // then
    expect(target).toBeNull();
  });
});
