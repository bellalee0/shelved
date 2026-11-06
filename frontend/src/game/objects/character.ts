import type * as Phaser from 'phaser';
import type { Direction, SpriteAnimation } from '../assets';
import {
  CHARACTER_BODY_HEIGHT,
  CHARACTER_BODY_OFFSET_X,
  CHARACTER_BODY_OFFSET_Y,
  CHARACTER_BODY_WIDTH,
  CHARACTER_ORIGIN_Y,
} from '../constants';

// 플레이어·NPC가 함께 쓰는 캐릭터 공통 처리

export const FALLBACK_CHARACTER_TEXTURE_KEY = 'fallback-character';

/** 기준점을 발밑에 두고 충돌 박스를 발밑 작은 영역으로 줄인다 */
export function applyCharacterBody(sprite: Phaser.Physics.Arcade.Sprite) {
  sprite.setOrigin(0.5, CHARACTER_ORIGIN_Y);
  const body = sprite.body as Phaser.Physics.Arcade.Body;
  body.setSize(CHARACTER_BODY_WIDTH, CHARACTER_BODY_HEIGHT, false);
  body.setOffset(CHARACTER_BODY_OFFSET_X, CHARACTER_BODY_OFFSET_Y);
}

export const walkAnimationKey = (textureKey: string, direction: Direction) =>
  `${textureKey}:walk-${direction}`;

/** 방향별 걷기 애니메이션을 한 번만 만든다. 멈춤 자세(idleFrame) 열은 걷기 동작에서 뺀다 */
export function ensureWalkAnimations(
  scene: Phaser.Scene,
  textureKey: string,
  animation: SpriteAnimation,
) {
  for (const direction of Object.keys(animation.rows) as Direction[]) {
    const key = walkAnimationKey(textureKey, direction);
    if (scene.anims.exists(key)) {
      continue;
    }
    const rowStart = animation.rows[direction] * animation.frameCount;
    const frames = Array.from({ length: animation.frameCount }, (_, column) => column)
      .filter((column) => column !== animation.idleFrame)
      .map((column) => ({ key: textureKey, frame: rowStart + column }));
    scene.anims.create({ key, frames, frameRate: animation.frameRate, repeat: -1 });
  }
}

/**
 * manifest에 없는 spriteKey용 대체 그림. 64×64 프레임 안에 눈에 띄는 자홍색 사각형을 그려
 * 실제 스프라이트와 같은 기준점·충돌 박스를 그대로 쓸 수 있게 한다.
 */
export function ensureFallbackCharacterTexture(scene: Phaser.Scene) {
  if (scene.textures.exists(FALLBACK_CHARACTER_TEXTURE_KEY)) {
    return;
  }
  const graphics = scene.make.graphics({}, false);
  graphics.fillStyle(0xff00ff, 1);
  graphics.fillRect(20, 14, 24, 46);
  graphics.lineStyle(2, 0x000000, 1);
  graphics.strokeRect(20, 14, 24, 46);
  graphics.generateTexture(FALLBACK_CHARACTER_TEXTURE_KEY, 64, 64);
  graphics.destroy();
}
