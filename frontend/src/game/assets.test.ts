import { describe, expect, it } from 'vitest';
import manifestJson from '../../public/assets/manifest.json';
import type { RoomMapSuspect } from '../features/room/types';
import {
  collectSpriteKeys,
  findSpriteEntry,
  frameIndexOf,
  parseManifest,
  resolveTilesetImages,
} from './assets';

const manifest = parseManifest(manifestJson)!;

const placed = (spriteKey: string | null, placedOnMap = true): RoomMapSuspect => ({
  suspectId: 1,
  name: '인물',
  spriteKey,
  canConverse: true,
  x: placedOnMap ? 16 : null,
  y: placedOnMap ? 16 : null,
});

describe('findSpriteEntry', () => {
  it('manifest의 spriteKey를 파일 경로로 바꾼다', () => {
    // when
    const entry = findSpriteEntry(manifest, 'npc-maid-01');

    // then
    expect(entry?.path).toBe('/assets/sprites/npc-maid-01.png');
  });

  it('없는 키는 null (객체 기본 속성 이름도 키로 취급하지 않는다)', () => {
    // when / then
    expect(findSpriteEntry(manifest, 'npc-unknown-99')).toBeNull();
    expect(findSpriteEntry(manifest, 'toString')).toBeNull();
  });
});

describe('collectSpriteKeys', () => {
  it('배치된 인물의 키만 모아 있는 것과 없는 것으로 나누고 중복을 없앤다', () => {
    // given
    const suspects = [
      placed('npc-maid-01'),
      placed('npc-maid-01'),
      placed('npc-unknown-99'),
      placed('npc-lady-01', false),
      placed(null),
    ];

    // when
    const result = collectSpriteKeys(manifest, suspects);

    // then
    expect(result).toEqual({ found: ['npc-maid-01'], missing: ['npc-unknown-99'] });
  });
});

describe('resolveTilesetImages', () => {
  it('맵 파일 기준 상대 경로를 사이트 경로로 바꾸고 이미지 없는 타일셋은 건너뛴다', () => {
    // given
    const map = {
      tilesets: [
        { name: 'interior-floors', image: '../tilesets/interior-floors.png' },
        { name: 'no-image' },
      ],
    };

    // when
    const images = resolveTilesetImages('/assets/maps/clocktower-mansion.json', map);

    // then
    expect(images).toEqual([
      {
        name: 'interior-floors',
        textureKey: 'tileset:interior-floors',
        path: '/assets/tilesets/interior-floors.png',
      },
    ]);
  });
});

describe('frameIndexOf', () => {
  it('방향 행 × 프레임 수 + 열로 프레임 번호를 구한다', () => {
    // when / then
    expect(frameIndexOf(manifest.player.animations.walk, 'down', 0)).toBe(18);
    expect(frameIndexOf(manifest.player.animations.walk, 'right', 3)).toBe(30);
  });
});

describe('parseManifest', () => {
  it('모양이 맞지 않으면 null', () => {
    // when / then
    expect(parseManifest(null)).toBeNull();
    expect(parseManifest({ version: 1 })).toBeNull();
  });
});
