import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setConnectionId } from '../../../shared/api/client';
import { EventBus } from '../../../game/EventBus';
import { GameEvents } from '../../../game/events';
import { getBriefing, getMap, getRoomState, pauseRoom, resumeRoom, startRoom } from '../api';
import type {
  RoomBriefingResponse,
  RoomConnectionResponse,
  RoomMapResponse,
  RoomStateResponse,
  RoomTimerResponse,
} from '../types';
import { useGameSession } from './useGameSession';

vi.mock('../api', () => ({
  getRoomState: vi.fn(),
  getBriefing: vi.fn(),
  getMap: vi.fn(),
  pauseRoom: vi.fn(),
  startRoom: vi.fn(),
  resumeRoom: vi.fn(),
  heartbeat: vi.fn(),
}));

vi.mock('../../../shared/api/client', () => ({ setConnectionId: vi.fn() }));

const ROOM_ID = 3;
const USER_ID = 7;

function timerOf(override: Partial<RoomTimerResponse> = {}): RoomTimerResponse {
  return {
    status: 'READY',
    timeLimitSec: 1200,
    elapsedActiveMs: 0,
    remainingMs: 1_200_000,
    awaitingGrading: false,
    serverTime: '2026-11-10T10:00:00',
    ...override,
  };
}

function stateOf(timer: RoomTimerResponse): RoomStateResponse {
  return { roomId: ROOM_ID, timer } as RoomStateResponse;
}

/** 호출한 쪽에서 직접 끝낼 수 있는 응답 */
function deferred<T>() {
  let resolve!: (value: T) => void;
  const promise = new Promise<T>((res) => {
    resolve = res;
  });
  return { promise, resolve };
}

function connectionOf(status: RoomTimerResponse['status']): RoomConnectionResponse {
  return {
    connectionId: 'late-connection',
    heartbeatIntervalMs: 5000,
    timer: timerOf({ status }),
  };
}

function renderSession() {
  const queryClient = new QueryClient({ defaultOptions: { queries: { retry: false } } });
  const wrapper = ({ children }: { children: ReactNode }) => (
    <QueryClientProvider client={queryClient}>
      <MemoryRouter>{children}</MemoryRouter>
    </QueryClientProvider>
  );
  return renderHook(() => useGameSession(ROOM_ID, USER_ID), { wrapper });
}

const placeAtSpawn = vi.fn();

/** setConnectionId가 실제 연결 값으로 불린 적이 있는지 */
function connectionWasSet() {
  return vi.mocked(setConnectionId).mock.calls.some(([id]) => id !== null);
}

beforeEach(() => {
  vi.mocked(pauseRoom).mockResolvedValue(timerOf());
  vi.mocked(getRoomState).mockResolvedValue(stateOf(timerOf()));
  vi.mocked(getBriefing).mockResolvedValue({} as RoomBriefingResponse);
  vi.mocked(getMap).mockResolvedValue({} as RoomMapResponse);
  EventBus.on(GameEvents.PLACE_AT_SPAWN, placeAtSpawn);
});

afterEach(() => {
  EventBus.off(GameEvents.PLACE_AT_SPAWN, placeAtSpawn);
  vi.clearAllMocks();
});

describe('useGameSession 늦게 도착한 시작·이어하기 응답', () => {
  it('start 응답 전에 화면을 떠나면 연결을 설정하지 않고 시작점 배치도 보내지 않는다', async () => {
    // given
    const pending = deferred<RoomConnectionResponse>();
    vi.mocked(startRoom).mockReturnValue(pending.promise);
    const { result, unmount } = renderSession();
    await waitFor(() => expect(result.current.phase).toBe('ready'));
    act(() => {
      void result.current.start();
    });

    // when
    unmount();
    await act(async () => pending.resolve(connectionOf('IN_PROGRESS')));

    // then
    expect(connectionWasSet()).toBe(false);
    expect(placeAtSpawn).not.toHaveBeenCalled();
  });

  it('resume 응답 전에 재입장 흐름이 시작되면 응답을 버리고 새 입장 결과를 따른다', async () => {
    // given
    vi.mocked(getRoomState).mockResolvedValue(
      stateOf(timerOf({ status: 'PAUSED', remainingMs: 600_000 })),
    );
    const pending = deferred<RoomConnectionResponse>();
    vi.mocked(resumeRoom).mockReturnValue(pending.promise);
    const { result } = renderSession();
    await waitFor(() => expect(result.current.phase).toBe('paused'));
    act(() => {
      void result.current.resume();
    });

    // when
    act(() => result.current.reenter());
    await waitFor(() => expect(result.current.phase).toBe('paused'));
    await act(async () => pending.resolve(connectionOf('IN_PROGRESS')));

    // then
    expect(result.current.phase).toBe('paused');
    expect(result.current.hasConnection).toBe(false);
    expect(result.current.isSubmitting).toBe(false);
    expect(connectionWasSet()).toBe(false);
    expect(placeAtSpawn).not.toHaveBeenCalled();
  });
});

describe('useGameSession 입장 시 맵 조회 실패', () => {
  it('종료 상태면 맵 조회가 실패해도 ended로 간다', async () => {
    // given
    vi.mocked(getRoomState).mockResolvedValue(stateOf(timerOf({ status: 'CLEARED' })));
    vi.mocked(getMap).mockRejectedValue(new Error('맵 실패'));

    // when
    const { result } = renderSession();

    // then
    await waitFor(() => expect(result.current.phase).toBe('ended'));
  });

  it('채점 대기면 맵·사전 정보 조회가 실패해도 awaitingGrading으로 간다', async () => {
    // given
    vi.mocked(getRoomState).mockResolvedValue(
      stateOf(timerOf({ status: 'PAUSED', remainingMs: 0, awaitingGrading: true })),
    );
    vi.mocked(getMap).mockRejectedValue(new Error('맵 실패'));
    vi.mocked(getBriefing).mockRejectedValue(new Error('사전 정보 실패'));

    // when
    const { result } = renderSession();

    // then
    await waitFor(() => expect(result.current.phase).toBe('awaitingGrading'));
    expect(result.current.mapData).toBeNull();
    expect(result.current.briefing).toBeNull();
  });

  it('진행할 수 있는 상태면 맵 조회가 실패했을 때 enterFailed로 간다', async () => {
    // given
    vi.mocked(getMap).mockRejectedValue(new Error('맵 실패'));

    // when
    const { result } = renderSession();

    // then
    await waitFor(() => expect(result.current.phase).toBe('enterFailed'));
    expect(result.current.enterError).toBe('맵 실패');
  });

  it('state 조회가 실패하면 enterFailed로 간다', async () => {
    // given
    vi.mocked(getRoomState).mockRejectedValue(new Error('상태 실패'));

    // when
    const { result } = renderSession();

    // then
    await waitFor(() => expect(result.current.phase).toBe('enterFailed'));
    expect(result.current.enterError).toBe('상태 실패');
  });

  it('세 요청은 응답을 기다리지 않고 함께 보낸다', async () => {
    // given
    vi.mocked(getRoomState).mockReturnValue(new Promise(() => undefined));

    // when
    renderSession();

    // then
    await waitFor(() => expect(getMap).toHaveBeenCalled());
    expect(getBriefing).toHaveBeenCalled();
  });
});
