import type * as Phaser from 'phaser';
import type { RoomMapResponse } from '../../features/room/types';
import type { AssetManifest } from '../assets';

export const SceneKeys = {
  BOOT: 'Boot',
  PRELOAD: 'Preload',
  INVESTIGATION: 'Investigation',
} as const;

// 게임 생성 시 React가 넘긴 맵 데이터는 game.registry(게임 전체 공용 저장소)에 둔다
const REGISTRY_MAP_DATA = 'mapData';
const REGISTRY_MANIFEST = 'manifest';

export function setMapData(game: Phaser.Game, mapData: RoomMapResponse) {
  game.registry.set(REGISTRY_MAP_DATA, mapData);
}

export function getMapData(scene: Phaser.Scene): RoomMapResponse {
  return scene.registry.get(REGISTRY_MAP_DATA) as RoomMapResponse;
}

export function setManifest(scene: Phaser.Scene, manifest: AssetManifest) {
  scene.registry.set(REGISTRY_MANIFEST, manifest);
}

export function getManifest(scene: Phaser.Scene): AssetManifest {
  return scene.registry.get(REGISTRY_MANIFEST) as AssetManifest;
}
