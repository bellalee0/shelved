import { render } from '@testing-library/react';
import { StrictMode } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { MOCK_CASES } from '../mocks/data/clocktowerCase';
import type { RoomMapResponse } from '../features/room/types';
import { listenFromScene } from './bridge';
import { EventBus } from './EventBus';
import { GameEvents } from './events';
import { PhaserGame } from './PhaserGame';

// jsdom에서는 Phaser를 그릴 수 없으므로 phaser 모듈을 가짜로 바꿔, 게임 생성·정리 호출만 확인한다
const { FakeGame } = vi.hoisted(() => {
  class FakeGame {
    static instances: FakeGame[] = [];
    readonly config: { parent: HTMLElement; callbacks: { preBoot: (game: unknown) => void } };
    readonly registry = { set: vi.fn(), get: vi.fn() };
    readonly destroy = vi.fn();
    constructor(config: FakeGame['config']) {
      this.config = config;
      config.callbacks.preBoot(this);
      FakeGame.instances.push(this);
    }
  }
  return { FakeGame };
});

vi.mock('phaser', () => ({
  Game: FakeGame,
  Scene: class {},
  AUTO: 0,
  Scale: { FIT: 'FIT', CENTER_BOTH: 'CENTER_BOTH' },
  Scenes: { Events: { SHUTDOWN: 'shutdown', DESTROY: 'destroy' } },
}));

const mockCase = MOCK_CASES[0];
const mapData: RoomMapResponse = {
  ...mockCase.map,
  suspects: mockCase.suspects,
  evidences: mockCase.evidences.map(({ evidenceId, name, x, y }) => ({ evidenceId, name, x, y })),
};

const aliveGames = () => FakeGame.instances.filter((game) => game.destroy.mock.calls.length === 0);

/** 실제 Scene 대신, 생성된 게임에 붙은 Scene이 EventBus를 구독한 것처럼 만든다 */
function subscribeAsScene(game: InstanceType<typeof FakeGame>) {
  const fakeScene = { game, events: { once: vi.fn() } };
  listenFromScene(fakeScene as never, GameEvents.SET_INPUT_ENABLED, () => {});
}

describe('PhaserGame', () => {
  it('마운트하면 게임 하나를 만들고 맵 데이터를 registry에 넣는다', () => {
    // given
    FakeGame.instances = [];

    // when
    const { getByTestId } = render(<PhaserGame mapData={mapData} />);

    // then
    expect(FakeGame.instances).toHaveLength(1);
    const [game] = FakeGame.instances;
    expect(game.config.parent).toBe(getByTestId('phaser-game'));
    expect(game.registry.set).toHaveBeenCalledWith('mapData', mapData);
  });

  it('언마운트하면 destroy(true)를 부르고 그 게임의 EventBus 구독을 해제한다', () => {
    // given
    FakeGame.instances = [];
    const { unmount } = render(<PhaserGame mapData={mapData} />);
    subscribeAsScene(FakeGame.instances[0]);
    expect(EventBus.listenerCount(GameEvents.SET_INPUT_ENABLED)).toBe(1);

    // when
    unmount();

    // then
    expect(FakeGame.instances[0].destroy).toHaveBeenCalledExactlyOnceWith(true);
    expect(EventBus.listenerCount(GameEvents.SET_INPUT_ENABLED)).toBe(0);
  });

  it('StrictMode에서 마운트가 두 번 일어나도 살아 있는 게임은 하나다', () => {
    // given
    FakeGame.instances = [];

    // when
    const { unmount } = render(
      <StrictMode>
        <PhaserGame mapData={mapData} />
      </StrictMode>,
    );

    // then
    expect(aliveGames()).toHaveLength(1);
    unmount();
    expect(aliveGames()).toHaveLength(0);
  });

  it('맵 데이터가 바뀌면 이전 게임을 없애고 새로 만든다', () => {
    // given
    FakeGame.instances = [];
    const { rerender } = render(<PhaserGame mapData={mapData} />);

    // when
    rerender(<PhaserGame mapData={{ ...mapData, spawnX: 144 }} />);

    // then
    expect(FakeGame.instances).toHaveLength(2);
    expect(FakeGame.instances[0].destroy).toHaveBeenCalledWith(true);
    expect(aliveGames()).toEqual([FakeGame.instances[1]]);
  });
});
