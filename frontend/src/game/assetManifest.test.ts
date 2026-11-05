import { describe, expect, it } from 'vitest';
import { MOCK_CASES } from '../mocks/data/clocktowerCase';

// Phaser가 아직 없으므로 렌더링 대신 public/assets 안의 파일을 직접 읽어 검사한다.
// 파일 목록은 키만 쓰고(내용을 불러오지 않음), JSON은 원문 문자열로 읽는다.
const ASSET_FILES = import.meta.glob('../../public/assets/**/*', { query: '?url' });
const JSON_TEXTS = import.meta.glob('../../public/assets/**/*.json', {
  query: '?raw',
  import: 'default',
  eager: true,
}) as Record<string, string>;

const PREFIX = '../../public';

const fileExists = (sitePath: string) => `${PREFIX}${sitePath}` in ASSET_FILES;
const readJson = (sitePath: string): unknown => JSON.parse(JSON_TEXTS[`${PREFIX}${sitePath}`]);

type Animation = { frameCount: number; frameRate: number; rows: Record<string, number> };
type SpriteEntry = {
  path: string;
  frameWidth: number;
  frameHeight: number;
  animations: { walk: Animation };
};
type Manifest = {
  version: number;
  player: SpriteEntry;
  sprites: Record<string, SpriteEntry>;
  maps: Record<string, string>;
};
type TiledLayer = { name: string; type: string; data?: number[]; width: number; height: number };
type TiledMap = {
  orientation: string;
  width: number;
  height: number;
  tilewidth: number;
  tileheight: number;
  layers: TiledLayer[];
  tilesets: { name: string; image?: string; source?: string; firstgid: number }[];
};

const KEBAB = /^[a-z0-9]+(-[a-z0-9]+)*$/;
const REQUIRED_LAYERS = ['ground', 'walls', 'decor', 'above', 'collision'];
const FORBIDDEN_WORDS = ['culprit', '범인', 'answer', '정답', 'trick', '트릭'];

const manifest = readJson('/assets/manifest.json') as Manifest;
const allSprites = { player: manifest.player, ...manifest.sprites };

describe('manifest.json', () => {
  it('모든 스프라이트·맵 경로에 실제 파일이 있다', () => {
    for (const sprite of Object.values(allSprites)) {
      expect(fileExists(sprite.path), sprite.path).toBe(true);
    }
    for (const mapPath of Object.values(manifest.maps)) {
      expect(fileExists(mapPath), mapPath).toBe(true);
    }
  });

  it('키가 kebab-case이고 스프라이트 정보가 갖춰져 있다', () => {
    for (const key of [...Object.keys(manifest.sprites), ...Object.keys(manifest.maps)]) {
      expect(key, key).toMatch(KEBAB);
    }
    for (const [key, sprite] of Object.entries(allSprites)) {
      expect(sprite.frameWidth, key).toBeGreaterThan(0);
      expect(sprite.frameHeight, key).toBeGreaterThan(0);
      expect(Object.keys(sprite.animations.walk.rows).sort(), key).toEqual([
        'down',
        'left',
        'right',
        'up',
      ]);
    }
  });
});

describe.each(Object.entries(manifest.maps))('맵 %s', (_key, mapPath) => {
  const map = readJson(mapPath) as TiledMap;

  it('직교 방향이고 타일셋이 JSON 안에 포함되어 있으며 이미지 파일이 있다', () => {
    expect(map.orientation).toBe('orthogonal');
    expect(map.tilesets.length).toBeGreaterThan(0);
    for (const tileset of map.tilesets) {
      expect(tileset.source, `${tileset.name}: 외부 타일셋 파일 사용 금지`).toBeUndefined();
      // 맵 JSON(/assets/maps/) 기준 상대 경로를 사이트 경로로 바꿔 확인한다.
      const imagePath = new URL(tileset.image ?? '', `http://x${mapPath}`).pathname;
      expect(fileExists(imagePath), imagePath).toBe(true);
    }
  });

  it('필수 레이어가 있고 데이터 크기가 맵 크기와 같다', () => {
    const names = map.layers.map((layer) => layer.name);
    expect(names).toEqual(expect.arrayContaining(REQUIRED_LAYERS));
    for (const layer of map.layers) {
      expect(layer.data?.length, layer.name).toBe(map.width * map.height);
    }
  });
});

describe('목 데이터와 manifest 일치', () => {
  it.each(MOCK_CASES.map((mockCase) => [mockCase.card.caseId, mockCase] as const))(
    '사건 %s: mapUrl·spriteKey가 manifest에 있고 좌표가 걸어갈 수 있는 곳이다',
    (_caseId, mockCase) => {
      expect(Object.values(manifest.maps)).toContain(mockCase.map.mapUrl);
      const map = readJson(mockCase.map.mapUrl) as TiledMap;
      const collision = map.layers.find((layer) => layer.name === mockCase.map.collisionLayer);
      expect(collision, '충돌 레이어').toBeDefined();

      const isWalkable = (x: number, y: number) => {
        const col = Math.floor(x / map.tilewidth);
        const row = Math.floor(y / map.tileheight);
        const inside = col >= 0 && row >= 0 && col < map.width && row < map.height;
        return inside && collision!.data![row * map.width + col] === 0;
      };

      expect(isWalkable(mockCase.map.spawnX, mockCase.map.spawnY), '시작 위치').toBe(true);
      for (const suspect of mockCase.suspects) {
        if (suspect.spriteKey !== null) {
          expect(Object.keys(manifest.sprites), suspect.name).toContain(suspect.spriteKey);
        }
        if (suspect.x !== null && suspect.y !== null) {
          expect(isWalkable(suspect.x, suspect.y), suspect.name).toBe(true);
        }
      }
      for (const evidence of mockCase.evidences) {
        expect(isWalkable(evidence.x, evidence.y), evidence.name).toBe(true);
      }
    },
  );
});

describe('공개 에셋 점검', () => {
  it('manifest와 맵 JSON에 정답 관련 금지어가 없다', () => {
    const publicTexts = Object.entries(JSON_TEXTS).filter(([path]) => !path.includes('/licenses/'));
    expect(publicTexts.length).toBeGreaterThanOrEqual(2);
    for (const [path, text] of publicTexts) {
      const lower = text.toLowerCase();
      for (const word of FORBIDDEN_WORDS) {
        expect(lower.includes(word), `${path}: ${word}`).toBe(false);
      }
    }
  });
});
