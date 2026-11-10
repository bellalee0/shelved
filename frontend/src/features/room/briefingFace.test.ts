import { describe, expect, it } from 'vitest';
import type { AssetManifest, SpriteEntry } from '../../game/assets';
import { findFaceEntry, nameInitial, spriteFaceStyle } from './briefingFace';

const entry: SpriteEntry = {
  path: '/assets/sprites/npc-test.png',
  frameWidth: 64,
  frameHeight: 64,
  animations: {
    walk: {
      frameCount: 9,
      frameRate: 8,
      idleFrame: 0,
      rows: { up: 0, left: 1, down: 2, right: 3 },
    },
  },
};
const manifest: AssetManifest = {
  version: 1,
  player: entry,
  sprites: { 'npc-test': entry },
  maps: {},
};

describe('spriteFaceStyle', () => {
  it('시트를 확대하고 아래를 보는 정지 프레임의 얼굴 위치로 옮긴다', () => {
    // given
    const boxPx = 40;

    // when
    const style = spriteFaceStyle(entry, boxPx);

    // then: 얼굴 영역은 프레임의 절반(32px)이라 40px 상자에는 1.25배로 키운다
    expect(style.backgroundImage).toBe('url(/assets/sprites/npc-test.png)');
    expect(style.backgroundSize).toBe('720px 320px');
    // 아래 방향은 3번째 행(y=128) + 위쪽 여백 3.84px, 1.25배, 가로는 프레임 안쪽 16px
    expect(style.backgroundPosition).toBe('-20px -164.8px');
    expect(style.imageRendering).toBe('pixelated');
  });
});

describe('findFaceEntry', () => {
  it('manifest에 있는 spriteKey는 스프라이트 항목을 돌려준다', () => {
    expect(findFaceEntry(manifest, 'npc-test')).toBe(entry);
  });

  it('spriteKey가 null이거나 manifest에 없거나 manifest가 없으면 null이다', () => {
    expect(findFaceEntry(manifest, null)).toBeNull();
    expect(findFaceEntry(manifest, 'npc-unknown')).toBeNull();
    expect(findFaceEntry(null, 'npc-test')).toBeNull();
  });
});

describe('nameInitial', () => {
  it('이름의 첫 글자를 돌려준다', () => {
    expect(nameInitial('엘리스')).toBe('엘');
    expect(nameInitial('  Tom')).toBe('T');
  });
});
