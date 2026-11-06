import * as Phaser from 'phaser';
import { MANIFEST_CACHE_KEY, MANIFEST_URL, MAP_CACHE_KEY, parseManifest } from '../assets';
import { hasLoadFailed, reportLoadFailure } from './loadFailure';
import { getMapData, SceneKeys, setManifest } from './sceneKeys';

/**
 * 첫 Scene. manifest.json과 맵 JSON을 읽는다.
 * 타일셋 이미지 경로는 맵 JSON 안에 있으므로, 이미지 로드는 다음 Scene(Preload)에서 한다.
 */
export class BootScene extends Phaser.Scene {
  constructor() {
    super(SceneKeys.BOOT);
  }

  preload() {
    const { mapUrl } = getMapData(this);
    this.load.on(Phaser.Loader.Events.FILE_LOAD_ERROR, (file: Phaser.Loader.File) => {
      reportLoadFailure(this, `${file.key} (${file.src})`);
    });
    this.load.json(MANIFEST_CACHE_KEY, MANIFEST_URL);
    this.load.tilemapTiledJSON(MAP_CACHE_KEY, mapUrl);
  }

  create() {
    if (hasLoadFailed(this)) {
      return;
    }
    const manifest = parseManifest(this.cache.json.get(MANIFEST_CACHE_KEY));
    if (!manifest) {
      reportLoadFailure(this, 'manifest 형식 오류');
      return;
    }
    setManifest(this, manifest);
    this.scene.start(SceneKeys.PRELOAD);
  }
}
