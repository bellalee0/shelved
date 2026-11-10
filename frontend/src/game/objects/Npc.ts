import type * as Phaser from 'phaser';
import type { RoomMapSuspect } from '../../features/room/types';
import {
  findSpriteEntry,
  frameIndexOf,
  spriteTextureKey,
  type AssetManifest,
  type SpriteEntry,
} from '../assets';
import { DEPTH_NAME_LABEL } from '../constants';
import { nameLabelPosition } from './nameLabel';
import {
  applyCharacterBody,
  ensureFallbackCharacterTexture,
  FALLBACK_CHARACTER_TEXTURE_KEY,
} from './character';

type PlacedSuspect = RoomMapSuspect & { x: number; y: number };

export function isPlaced(suspect: RoomMapSuspect): suspect is PlacedSuspect {
  return suspect.x !== null && suspect.y !== null;
}

function downIdleFrame(entry: SpriteEntry) {
  const walk = entry.animations.walk;
  return frameIndexOf(walk, 'down', walk.idleFrame);
}

/**
 * 맵에 서 있는 NPC. 아래를 보는 정지 자세로 그리고, 움직이지 않는 충돌 몸체로 플레이어를 막는다.
 * spriteKey가 manifest에 없으면 대체 그림으로 그리고 키만 경고로 남긴다.
 */
export function createNpc(
  scene: Phaser.Scene,
  suspect: PlacedSuspect,
  manifest: AssetManifest,
): Phaser.Physics.Arcade.Sprite {
  const entry = suspect.spriteKey === null ? null : findSpriteEntry(manifest, suspect.spriteKey);
  let sprite: Phaser.Physics.Arcade.Sprite;
  if (entry) {
    const textureKey = spriteTextureKey(suspect.spriteKey as string);
    sprite = scene.physics.add.sprite(suspect.x, suspect.y, textureKey, downIdleFrame(entry));
  } else {
    console.warn(`[game] manifest에 없는 spriteKey: ${suspect.spriteKey}`);
    ensureFallbackCharacterTexture(scene);
    sprite = scene.physics.add.sprite(suspect.x, suspect.y, FALLBACK_CHARACTER_TEXTURE_KEY);
  }
  applyCharacterBody(sprite);
  sprite.setImmovable(true);
  sprite.setPushable(false);
  (sprite.body as Phaser.Physics.Arcade.Body).moves = false;
  sprite.setDepth(suspect.y);
  return sprite;
}

/**
 * NPC 머리 위에 항상 떠 있는 이름. 픽셀 아트 위에서도 읽히도록 반투명 배경 상자를 깔고,
 * 해상도를 2배로 해 확대해도 선명하게 한다. 이름은 화면에만 그리고 콘솔에는 남기지 않는다.
 */
export function createNpcNameLabel(
  scene: Phaser.Scene,
  suspect: PlacedSuspect,
): Phaser.GameObjects.Text {
  const { x, y } = nameLabelPosition(suspect.x, suspect.y);
  return scene.add
    .text(x, y, suspect.name, {
      fontFamily: 'sans-serif',
      fontSize: '11px',
      color: '#ffffff',
      backgroundColor: '#000000aa',
      padding: { x: 4, y: 2 },
    })
    .setOrigin(0.5, 1)
    .setDepth(DEPTH_NAME_LABEL)
    .setResolution(2);
}
