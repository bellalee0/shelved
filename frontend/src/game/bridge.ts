import * as Phaser from 'phaser';
import { EventBus, type GameEventHandler } from './EventBus';
import type { GameEventName, GameEventPayloads } from './events';

// Scene이 EventBus를 쓸 때 거치는 연결부.
// Phaser의 game.destroy()는 다음 프레임에야 실제로 끝나므로, 그 사이 남은 리스너·이벤트가
// 새 게임(StrictMode 재마운트, 맵 교체)과 섞이지 않도록 React가 끊는 순간 바로 정리한다.

const disposersByGame = new WeakMap<Phaser.Game, Set<() => void>>();
const disconnectedGames = new WeakSet<Phaser.Game>();

type EmitArgs<K extends GameEventName> = GameEventPayloads[K] extends void
  ? []
  : [payload: GameEventPayloads[K]];

/** Scene이 React 이벤트를 구독한다. Scene 종료나 disconnectGame 중 먼저 오는 쪽에서 해제된다 */
export function listenFromScene<K extends GameEventName>(
  scene: Phaser.Scene,
  event: K,
  handler: GameEventHandler<K>,
) {
  const { game } = scene;
  if (disconnectedGames.has(game)) {
    return;
  }
  let disposers = disposersByGame.get(game);
  if (!disposers) {
    disposers = new Set();
    disposersByGame.set(game, disposers);
  }
  const dispose = () => {
    EventBus.off(event, handler);
    disposers.delete(dispose);
  };
  EventBus.on(event, handler);
  disposers.add(dispose);
  scene.events.once(Phaser.Scenes.Events.SHUTDOWN, dispose);
  scene.events.once(Phaser.Scenes.Events.DESTROY, dispose);
}

/** Scene이 React로 이벤트를 보낸다. React가 이미 끊은 게임이면 보내지 않는다 */
export function emitFromScene<K extends GameEventName>(
  scene: Phaser.Scene,
  event: K,
  ...args: EmitArgs<K>
) {
  if (!disconnectedGames.has(scene.game)) {
    EventBus.emit(event, ...args);
  }
}

/** React가 게임을 내릴 때 destroy 전에 부른다. 이 게임의 모든 EventBus 구독을 즉시 해제한다 */
export function disconnectGame(game: Phaser.Game) {
  disconnectedGames.add(game);
  for (const dispose of [...(disposersByGame.get(game) ?? [])]) {
    dispose();
  }
  disposersByGame.delete(game);
}
