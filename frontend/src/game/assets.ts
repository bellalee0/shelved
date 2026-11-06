import type { RoomMapSuspect } from '../features/room/types';

// public/assets/manifest.json을 읽어 spriteKey·맵 경로를 실제 파일 경로로 바꾼다 (frontend.md "에셋 관리").
// Phaser에 의존하지 않는 순수 함수만 둔다. 실제 로드는 scenes/에서 한다.

export const MANIFEST_URL = '/assets/manifest.json';

export type Direction = 'up' | 'left' | 'down' | 'right';

export type SpriteAnimation = {
  frameCount: number;
  frameRate: number;
  idleFrame: number;
  rows: Record<Direction, number>;
};

export type SpriteEntry = {
  path: string;
  frameWidth: number;
  frameHeight: number;
  animations: { walk: SpriteAnimation };
};

export type AssetManifest = {
  version: number;
  player: SpriteEntry;
  sprites: Record<string, SpriteEntry>;
  maps: Record<string, string>;
};

/** 맵 JSON 중 타일셋 이미지 경로 계산에 필요한 부분 */
export type TiledMapTilesets = {
  tilesets: { name: string; image?: string }[];
};

export type TilesetImage = {
  /** 맵 JSON의 타일셋 이름. 맵 제작 규칙상 이미지 파일 이름(확장자 제외)과 같다 */
  name: string;
  textureKey: string;
  path: string;
};

// Phaser 텍스처 키. 종류별 접두사로 같은 이름이 겹치지 않게 한다
export const PLAYER_TEXTURE_KEY = 'player';
export const MAP_CACHE_KEY = 'room-map';
export const MANIFEST_CACHE_KEY = 'asset-manifest';
export const tilesetTextureKey = (name: string) => `tileset:${name}`;
export const spriteTextureKey = (spriteKey: string) => `sprite:${spriteKey}`;

function isRecord(value: unknown): value is Record<string, unknown> {
  return typeof value === 'object' && value !== null;
}

/** 읽어 온 manifest가 최소한의 모양을 갖췄는지 확인한다. 아니면 null */
export function parseManifest(json: unknown): AssetManifest | null {
  if (!isRecord(json) || !isRecord(json.player) || !isRecord(json.sprites)) {
    return null;
  }
  return json as AssetManifest;
}

/** spriteKey → manifest 항목. 없는 키면 null (호출하는 쪽이 대체 표시를 그린다) */
export function findSpriteEntry(manifest: AssetManifest, spriteKey: string): SpriteEntry | null {
  return Object.hasOwn(manifest.sprites, spriteKey) ? manifest.sprites[spriteKey] : null;
}

/** 맵에 배치된 인물의 spriteKey를 manifest에 있는 것(found)과 없는 것(missing)으로 나눈다. 중복은 하나로 */
export function collectSpriteKeys(manifest: AssetManifest, suspects: RoomMapSuspect[]) {
  const keys = new Set(
    suspects
      .filter((suspect) => suspect.x !== null && suspect.y !== null && suspect.spriteKey !== null)
      .map((suspect) => suspect.spriteKey as string),
  );
  const found: string[] = [];
  const missing: string[] = [];
  for (const key of keys) {
    (findSpriteEntry(manifest, key) ? found : missing).push(key);
  }
  return { found, missing };
}

const PLACEHOLDER_ORIGIN = 'http://shelved.invalid';

/**
 * 타일셋 이미지 경로. 맵 JSON의 tilesets[].image는 맵 파일 기준 상대 경로(예: ../tilesets/a.png)라
 * mapUrl을 기준으로 사이트 경로(/assets/tilesets/a.png)로 바꾼다. 이미지가 없는 타일셋은 건너뛴다.
 */
export function resolveTilesetImages(mapUrl: string, map: TiledMapTilesets): TilesetImage[] {
  const base = new URL(mapUrl, PLACEHOLDER_ORIGIN);
  return map.tilesets
    .filter((tileset) => typeof tileset.image === 'string' && tileset.image !== '')
    .map((tileset) => {
      const url = new URL(tileset.image as string, base);
      const path = url.origin === PLACEHOLDER_ORIGIN ? url.pathname : url.href;
      return { name: tileset.name, textureKey: tilesetTextureKey(tileset.name), path };
    });
}

/** 스프라이트 시트의 프레임 번호. 시트는 rows[방향] 행 × frameCount 열이다 */
export function frameIndexOf(animation: SpriteAnimation, direction: Direction, column: number) {
  return animation.rows[direction] * animation.frameCount + column;
}
