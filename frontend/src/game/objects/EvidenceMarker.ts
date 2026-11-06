import type * as Phaser from 'phaser';
import type { RoomMapEvidence } from '../../features/room/types';

// 물증 에셋이 없어 코드로 그린 반짝이는 원으로 표시한다. 충돌 몸체가 없어 지나갈 수 있다.

const EVIDENCE_MARKER_TEXTURE_KEY = 'evidence-marker';
const MARKER_SIZE = 16;

function ensureMarkerTexture(scene: Phaser.Scene) {
  if (scene.textures.exists(EVIDENCE_MARKER_TEXTURE_KEY)) {
    return;
  }
  const graphics = scene.make.graphics({}, false);
  const center = MARKER_SIZE / 2;
  graphics.fillStyle(0xffd54a, 1);
  graphics.fillCircle(center, center, 5);
  graphics.lineStyle(2, 0xffffff, 1);
  graphics.strokeCircle(center, center, 6);
  graphics.generateTexture(EVIDENCE_MARKER_TEXTURE_KEY, MARKER_SIZE, MARKER_SIZE);
  graphics.destroy();
}

export function createEvidenceMarker(scene: Phaser.Scene, evidence: RoomMapEvidence) {
  ensureMarkerTexture(scene);
  const marker = scene.add.image(evidence.x, evidence.y, EVIDENCE_MARKER_TEXTURE_KEY);
  // 바닥에 놓인 물건이므로 같은 줄의 캐릭터보다 뒤에 그린다
  marker.setDepth(evidence.y - MARKER_SIZE);
  scene.tweens.add({
    targets: marker,
    scale: { from: 0.8, to: 1.25 },
    alpha: { from: 1, to: 0.55 },
    duration: 700,
    yoyo: true,
    repeat: -1,
    ease: 'Sine.easeInOut',
  });
  return marker;
}
