import * as Phaser from 'phaser';
import type { RoomMapResponse } from '../features/room/types';
import { GAME_HEIGHT, GAME_WIDTH } from './constants';
import { BootScene } from './scenes/BootScene';
import { InvestigationScene } from './scenes/InvestigationScene';
import { PreloadScene } from './scenes/PreloadScene';
import { setMapData } from './scenes/sceneKeys';

/** Phaser 게임을 만든다. 맵 데이터는 이벤트가 아니라 생성 시 초기값(game.registry)으로 넘긴다 */
export function createGame(parent: HTMLElement, mapData: RoomMapResponse) {
  return new Phaser.Game({
    type: Phaser.AUTO,
    parent,
    width: GAME_WIDTH,
    height: GAME_HEIGHT,
    backgroundColor: '#1b1712',
    pixelArt: true,
    banner: false,
    scale: { mode: Phaser.Scale.FIT, autoCenter: Phaser.Scale.CENTER_BOTH },
    physics: { default: 'arcade', arcade: { debug: false } },
    // 키 가로채기를 하지 않는다 (입력창에 글자가 들어가야 한다)
    input: { keyboard: { capture: [] } },
    scene: [BootScene, PreloadScene, InvestigationScene],
    callbacks: { preBoot: (game) => setMapData(game, mapData) },
  });
}
