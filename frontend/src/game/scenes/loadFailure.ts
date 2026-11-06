import type * as Phaser from 'phaser';
import { emitFromScene } from '../bridge';
import { GameEvents } from '../events';

const REGISTRY_LOAD_FAILED = 'loadFailed';

/** 맵·타일셋·스프라이트 로드 실패를 React에 한 번만 알린다. 이후 Scene 진행은 멈춘다 */
export function reportLoadFailure(scene: Phaser.Scene, reason: string) {
  if (scene.registry.get(REGISTRY_LOAD_FAILED) === true) {
    return;
  }
  scene.registry.set(REGISTRY_LOAD_FAILED, true);
  console.warn(`[game] 맵 로드 실패: ${reason}`);
  emitFromScene(scene, GameEvents.MAP_LOAD_FAILED);
}

export function hasLoadFailed(scene: Phaser.Scene) {
  return scene.registry.get(REGISTRY_LOAD_FAILED) === true;
}
