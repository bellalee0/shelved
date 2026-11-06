import { describe, expect, it, vi } from 'vitest';
import { TypedEventBus } from './EventBus';
import { GameEvents } from './events';

describe('TypedEventBus', () => {
  it('구독한 이벤트에 데이터를 전달한다', () => {
    // given
    const bus = new TypedEventBus();
    const handler = vi.fn();
    bus.on(GameEvents.INTERACT_REQUESTED, handler);

    // when
    bus.emit(GameEvents.INTERACT_REQUESTED, { type: 'SUSPECT', targetId: 3 });

    // then
    expect(handler).toHaveBeenCalledExactlyOnceWith({ type: 'SUSPECT', targetId: 3 });
  });

  it('데이터가 없는 이벤트도 보낼 수 있고 다른 이벤트 구독자는 호출하지 않는다', () => {
    // given
    const bus = new TypedEventBus();
    const readyHandler = vi.fn();
    const spawnHandler = vi.fn();
    bus.on(GameEvents.MAP_READY, readyHandler);
    bus.on(GameEvents.PLACE_AT_SPAWN, spawnHandler);

    // when
    bus.emit(GameEvents.MAP_READY);

    // then
    expect(readyHandler).toHaveBeenCalledTimes(1);
    expect(spawnHandler).not.toHaveBeenCalled();
  });

  it('off 후에는 호출되지 않는다', () => {
    // given
    const bus = new TypedEventBus();
    const handler = vi.fn();
    bus.on(GameEvents.SET_INPUT_ENABLED, handler);

    // when
    bus.off(GameEvents.SET_INPUT_ENABLED, handler);
    bus.emit(GameEvents.SET_INPUT_ENABLED, { enabled: true });

    // then
    expect(handler).not.toHaveBeenCalled();
    expect(bus.listenerCount(GameEvents.SET_INPUT_ENABLED)).toBe(0);
  });

  it('같은 함수를 두 번 구독해도 한 번만 호출된다', () => {
    // given
    const bus = new TypedEventBus();
    const handler = vi.fn();
    bus.on(GameEvents.MAP_LOAD_FAILED, handler);
    bus.on(GameEvents.MAP_LOAD_FAILED, handler);

    // when
    bus.emit(GameEvents.MAP_LOAD_FAILED);

    // then
    expect(handler).toHaveBeenCalledTimes(1);
  });
});
