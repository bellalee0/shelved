import type { RoomMapEvidence, RoomMapSuspect } from '../features/room/types';
import type { InteractTargetType } from './events';

// 상호작용 대상 선택. Phaser에 의존하지 않는 순수 함수로 두어 단위 테스트한다.

export type Point = { x: number; y: number };

export type InteractionTarget = {
  type: InteractTargetType;
  targetId: number;
  x: number;
  y: number;
};

/** 맵에 배치되어 E키로 상호작용할 수 있는 대상 목록 (대화 불가·미배치 인물 제외, 물증은 모두) */
export function listInteractionTargets(
  suspects: RoomMapSuspect[],
  evidences: RoomMapEvidence[],
): InteractionTarget[] {
  const suspectTargets = suspects.flatMap((suspect): InteractionTarget[] =>
    suspect.canConverse && suspect.x !== null && suspect.y !== null
      ? [{ type: 'SUSPECT', targetId: suspect.suspectId, x: suspect.x, y: suspect.y }]
      : [],
  );
  const evidenceTargets = evidences.map((evidence): InteractionTarget => ({
    type: 'EVIDENCE',
    targetId: evidence.evidenceId,
    x: evidence.x,
    y: evidence.y,
  }));
  return [...suspectTargets, ...evidenceTargets];
}

/** 반경(px) 안에서 플레이어와 가장 가까운 대상 하나. 없으면 null. 거리가 같으면 목록 앞쪽 */
export function findNearestTarget(
  player: Point,
  targets: InteractionTarget[],
  radiusPx: number,
): InteractionTarget | null {
  let nearest: InteractionTarget | null = null;
  let nearestDistanceSq = radiusPx * radiusPx;
  for (const target of targets) {
    const dx = target.x - player.x;
    const dy = target.y - player.y;
    const distanceSq = dx * dx + dy * dy;
    if (distanceSq <= nearestDistanceSq && (nearest === null || distanceSq < nearestDistanceSq)) {
      nearest = target;
      nearestDistanceSq = distanceSq;
    }
  }
  return nearest;
}
