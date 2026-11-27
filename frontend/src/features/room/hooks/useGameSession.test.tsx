import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, renderHook, waitFor } from '@testing-library/react';
import type { ReactNode } from 'react';
import { MemoryRouter } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setConnectionId } from '../../../shared/api/client';
import { EventBus } from '../../../game/EventBus';
import { GameEvents } from '../../../game/events';
import {
  getBriefing,
  getMap,
  getRoomState,
  heartbeat,
  pauseRoom,
  resumeRoom,
  startRoom,
} from '../api';
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

describe('useGameSession 채점 대기 중 하트비트', () => {
  const INTERVAL_MS = 20;
  const GRACE = timerOf({ status: 'IN_PROGRESS', remainingMs: 0, awaitingGrading: true });
  const sleep = (ms: number) => act(() => new Promise((resolve) => setTimeout(resolve, ms)));
  const beats = () => vi.mocked(heartbeat).mock.calls.length;

  /** 수사를 시작한 뒤, 상태 재조회가 유예 구간(진행 중 + 남은 시간 0 + 채점 대기)을 돌려준 화면 */
  async function enterGracePeriod() {
    vi.mocked(startRoom).mockResolvedValue({
      connectionId: 'live-connection',
      heartbeatIntervalMs: INTERVAL_MS,
      timer: timerOf({ status: 'IN_PROGRESS' }),
    });
    vi.mocked(heartbeat).mockResolvedValue(GRACE);
    const view = renderSession();
    await waitFor(() => expect(view.result.current.phase).toBe('ready'));
    await act(() => view.result.current.start());
    vi.mocked(getRoomState).mockResolvedValue(stateOf(GRACE));
    await act(() => view.result.current.checkState());
    return view;
  }

  it('채점 대기 화면이어도 서버가 진행 중이면 연결을 유지하고 하트비트를 계속 보낸다', async () => {
    // given
    const { result, unmount } = await enterGracePeriod();
    const before = beats();

    // when
    await sleep(INTERVAL_MS * 4);

    // then
    expect(result.current.phase).toBe('awaitingGrading');
    expect(result.current.hasConnection).toBe(true);
    expect(beats()).toBeGreaterThan(before + 1);
    unmount();
  });

  it.each<[string, Partial<RoomTimerResponse>, string]>([
    [
      '시간을 확정한 PAUSED(채점 대기)',
      { status: 'PAUSED', remainingMs: 0, awaitingGrading: true },
      'awaitingGrading',
    ],
    ['EXPIRED', { status: 'EXPIRED', remainingMs: 0 }, 'ended'],
    ['CLEARED', { status: 'CLEARED', remainingMs: 0 }, 'ended'],
  ])('하트비트 응답이 %s면 연결을 지우고 하트비트를 멈춘다', async (_, override, phase) => {
    // given
    const { result, unmount } = await enterGracePeriod();

    // when
    vi.mocked(heartbeat).mockResolvedValue(timerOf(override));
    await waitFor(() => expect(result.current.hasConnection).toBe(false));
    const stopped = beats();
    await sleep(INTERVAL_MS * 4);

    // then
    expect(result.current.phase).toBe(phase);
    expect(beats()).toBe(stopped);
    unmount();
  });

  it('유예 구간에 채점이 끝나도 맵으로 돌아가지 않고, 다음 하트비트가 종료를 확정할 때까지 기다린다', async () => {
    // given
    const { result, unmount } = await enterGracePeriod();
    const resolved = timerOf({ status: 'IN_PROGRESS', remainingMs: 0 });
    vi.mocked(heartbeat).mockResolvedValue(resolved);
    vi.mocked(getRoomState).mockResolvedValue(stateOf(resolved));

    // when
    await act(() => result.current.checkState());
    await sleep(INTERVAL_MS * 3);

    // then
    expect(result.current.phase).toBe('awaitingGrading');
    expect(result.current.hasConnection).toBe(true);

    // when
    vi.mocked(heartbeat).mockResolvedValue(timerOf({ status: 'EXPIRED', remainingMs: 0 }));

    // then
    await waitFor(() => expect(result.current.phase).toBe('ended'));
    unmount();
  });

  it('연결 없이 들어온 유예 구간(새로고침·다른 화면)은 하트비트를 보내지 않는다', async () => {
    // given
    vi.mocked(getRoomState).mockResolvedValue(stateOf(GRACE));

    // when
    const { result } = renderSession();
    await waitFor(() => expect(result.current.phase).toBe('awaitingGrading'));
    await sleep(INTERVAL_MS * 3);

    // then
    expect(result.current.hasConnection).toBe(false);
    expect(heartbeat).not.toHaveBeenCalled();
  });
});
