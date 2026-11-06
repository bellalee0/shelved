import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, render, screen } from '@testing-library/react';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../app/routes';
import type { RoomMapResponse } from '../features/room/types';
import { EventBus } from '../game/EventBus';
import { GameEvents } from '../game/events';
import { seedMockRoom } from '../mocks/roomDb';
import { loginMockUser, resetLoginState } from '../mocks/testing/loginMockUser';

// Phaser는 jsdom에서 그릴 수 없으므로 PhaserGame을 받은 Props만 기록하는 가짜로 바꾼다
const { receivedMapData } = vi.hoisted(() => ({ receivedMapData: [] as RoomMapResponse[] }));

vi.mock('../game/PhaserGame', () => ({
  PhaserGame: ({ mapData }: { mapData: RoomMapResponse }) => {
    receivedMapData.push(mapData);
    return <div data-testid="phaser-game-mock" />;
  },
}));

function renderAt(path: string) {
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={createMemoryRouter(routes, { initialEntries: [path] })} />
    </QueryClientProvider>,
  );
}

beforeEach(() => {
  resetLoginState();
  receivedMapData.length = 0;
});

describe('게임 화면 (개발 확인용)', () => {
  it('맵 응답을 PhaserGame에 넘긴다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'READY' });

    // when
    renderAt(`/rooms/${room.roomId}`);

    // then
    expect(await screen.findByTestId('phaser-game-mock')).toBeInTheDocument();
    const mapData = receivedMapData.at(-1)!;
    expect(mapData.mapUrl).toBe('/assets/maps/clocktower-mansion.json');
    expect(mapData.suspects).toHaveLength(4);
    expect(mapData.evidences).toHaveLength(2);
  });

  it('MAP_READY를 받으면 입력을 허용하고, INTERACT_REQUESTED를 받으면 마지막 이벤트를 표시한다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'READY' });
    const inputHandler = vi.fn();
    EventBus.on(GameEvents.SET_INPUT_ENABLED, inputHandler);
    renderAt(`/rooms/${room.roomId}`);
    await screen.findByTestId('phaser-game-mock');

    // when
    act(() => {
      EventBus.emit(GameEvents.MAP_READY);
      EventBus.emit(GameEvents.INTERACT_REQUESTED, { type: 'EVIDENCE', targetId: 2 });
    });

    // then
    expect(inputHandler).toHaveBeenCalledExactlyOnceWith({ enabled: true });
    expect(screen.getByTestId('last-interaction')).toHaveTextContent('EVIDENCE #2');
    expect(screen.queryByText('맵을 불러오는 중…')).not.toBeInTheDocument();
    EventBus.off(GameEvents.SET_INPUT_ENABLED, inputHandler);
  });

  it('MAP_LOAD_FAILED를 받으면 오류 안내와 다시 시도를 보여 준다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'READY' });
    renderAt(`/rooms/${room.roomId}`);
    await screen.findByTestId('phaser-game-mock');

    // when
    act(() => EventBus.emit(GameEvents.MAP_LOAD_FAILED));

    // then
    expect(screen.getByRole('alert')).toHaveTextContent('맵을 불러오지 못했습니다.');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });
});
