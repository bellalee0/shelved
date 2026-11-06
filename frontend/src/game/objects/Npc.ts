import type * as Phaser from 'phaser';
import type { RoomMapSuspect } from '../../features/room/types';
import {
  findSpriteEntry,
  frameIndexOf,
  spriteTextureKey,
  type AssetManifest,
  type SpriteEntry,
} from '../assets';
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
