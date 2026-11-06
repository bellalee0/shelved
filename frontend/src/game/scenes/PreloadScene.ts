import * as Phaser from 'phaser';
import {
  collectSpriteKeys,
  findSpriteEntry,
  MAP_CACHE_KEY,
  PLAYER_TEXTURE_KEY,
  resolveTilesetImages,
  spriteTextureKey,
  type SpriteEntry,
  type TiledMapTilesets,
} from '../assets';
import { hasLoadFailed, reportLoadFailure } from './loadFailure';
import { getManifest, getMapData, SceneKeys } from './sceneKeys';

function isTiledMap(data: unknown): data is TiledMapTilesets {
  return (
    typeof data === 'object' &&
    data !== null &&
    Array.isArray((data as TiledMapTilesets).tilesets) &&
    Array.isArray((data as { layers?: unknown }).layers)
  );
}

/** 맵이 쓰는 타일셋 이미지와 플레이어·배치된 NPC 스프라이트 시트를 읽는다 */
export class PreloadScene extends Phaser.Scene {
  constructor() {
    super(SceneKeys.PRELOAD);
  }

  preload() {
    const mapData = getMapData(this);
    const manifest = getManifest(this);
    const mapJson: unknown = this.cache.tilemap.get(MAP_CACHE_KEY)?.data;
    if (!isTiledMap(mapJson)) {
      reportLoadFailure(this, '맵 JSON 형식 오류');
      return;
    }

    this.load.on(Phaser.Loader.Events.FILE_LOAD_ERROR, (file: Phaser.Loader.File) => {
      reportLoadFailure(this, `${file.key} (${file.src})`);
    });

    for (const tileset of resolveTilesetImages(mapData.mapUrl, mapJson)) {
      this.load.image(tileset.textureKey, tileset.path);
    }

    const loadSheet = (textureKey: string, entry: SpriteEntry) =>
      this.load.spritesheet(textureKey, entry.path, {
        frameWidth: entry.frameWidth,
        frameHeight: entry.frameHeight,
      });
    loadSheet(PLAYER_TEXTURE_KEY, manifest.player);

    // manifest에 없는 키는 로드하지 않고, Investigation에서 대체 표시로 그린다
    const { found } = collectSpriteKeys(manifest, mapData.suspects);
    for (const spriteKey of found) {
      loadSheet(spriteTextureKey(spriteKey), findSpriteEntry(manifest, spriteKey)!);
    }
  }

  create() {
    if (!hasLoadFailed(this)) {
      this.scene.start(SceneKeys.INVESTIGATION);
    }
  }
}
