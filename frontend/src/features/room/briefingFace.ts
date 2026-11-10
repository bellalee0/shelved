import type { CSSProperties } from 'react';
import { findSpriteEntry, type AssetManifest, type SpriteEntry } from '../../game/assets';

// 64×64 프레임에서 얼굴(머리)이 있는 정사각형 영역. 프레임 크기에 대한 비율이며, 실제 시트를 보며 맞춘 잠정값이다.
const FACE_CROP = { x: 0.25, y: 0.06, size: 0.5 };

/**
 * 스프라이트 시트에서 아래를 보는 정지 프레임의 얼굴 부분만 보여 주는 CSS 스타일.
 * 시트 전체를 배경 이미지로 깔고, 확대 비율만큼 키운 뒤 얼굴 영역이 상자에 맞도록 위치를 옮긴다.
 */
export function spriteFaceStyle(entry: SpriteEntry, boxPx: number): CSSProperties {
  const { frameWidth, frameHeight, animations } = entry;
  const walk = animations.walk;
  const rowCount = Math.max(...Object.values(walk.rows)) + 1;
  const scale = boxPx / (frameWidth * FACE_CROP.size);
  const left = walk.idleFrame * frameWidth + frameWidth * FACE_CROP.x;
  const top = walk.rows.down * frameHeight + frameHeight * FACE_CROP.y;
  return {
    width: boxPx,
    height: boxPx,
    backgroundImage: `url(${entry.path})`,
    backgroundRepeat: 'no-repeat',
    backgroundSize: `${frameWidth * walk.frameCount * scale}px ${frameHeight * rowCount * scale}px`,
    backgroundPosition: `${-left * scale}px ${-top * scale}px`,
    imageRendering: 'pixelated',
  };
}

/** 얼굴로 쓸 스프라이트 항목. spriteKey가 없거나 manifest에 없으면 null (이름 첫 글자 원으로 대신한다) */
export function findFaceEntry(manifest: AssetManifest | null, spriteKey: string | null) {
  if (manifest === null || spriteKey === null) {
    return null;
  }
  return findSpriteEntry(manifest, spriteKey);
}

/** 이름 첫 글자. 이모지 같은 두 칸짜리 글자도 깨지지 않게 코드 포인트 단위로 자른다 */
export function nameInitial(name: string) {
  return Array.from(name.trim())[0] ?? '?';
}
