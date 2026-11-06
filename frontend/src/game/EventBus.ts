import type { GameEventName, GameEventPayloads } from './events';

// payload가 void인 이벤트는 emit(name)만으로 보낼 수 있게 한다
type EmitArgs<K extends GameEventName> = GameEventPayloads[K] extends void
  ? []
  : [payload: GameEventPayloads[K]];

export type GameEventHandler<K extends GameEventName> = (...args: EmitArgs<K>) => void;

type AnyHandler = (payload?: unknown) => void;

/**
 * 타입이 붙은 작은 이벤트 통로. Phaser의 EventEmitter를 쓰지 않아
 * React 코드·테스트가 Phaser(용량이 큼)를 불러오지 않아도 된다.
 */
export class TypedEventBus {
  private readonly handlers = new Map<GameEventName, Set<AnyHandler>>();

  on<K extends GameEventName>(event: K, handler: GameEventHandler<K>) {
    let set = this.handlers.get(event);
    if (!set) {
      set = new Set();
      this.handlers.set(event, set);
    }
    set.add(handler as AnyHandler);
  }

  off<K extends GameEventName>(event: K, handler: GameEventHandler<K>) {
    this.handlers.get(event)?.delete(handler as AnyHandler);
  }

  emit<K extends GameEventName>(event: K, ...args: EmitArgs<K>) {
    // 처리 중에 off가 불려도 이번 호출은 그대로 끝나도록 복사본을 돈다
    for (const handler of [...(this.handlers.get(event) ?? [])]) {
      (handler as (...handlerArgs: EmitArgs<K>) => void)(...args);
    }
  }

  /** 등록된 리스너 수. 정리 누락을 테스트에서 확인하는 용도 */
  listenerCount(event: GameEventName) {
    return this.handlers.get(event)?.size ?? 0;
  }
}

/** 앱 전체에서 하나만 쓰는 React ↔ Phaser 이벤트 통로 */
export const EventBus = new TypedEventBus();
