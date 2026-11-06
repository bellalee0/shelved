import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/api/apiError';
import { heartbeat } from '../api';
import type { RoomTimerResponse } from '../types';
import { useHeartbeat } from './useHeartbeat';

vi.mock('../api', () => ({ heartbeat: vi.fn() }));

const heartbeatMock = vi.mocked(heartbeat);
const INTERVAL_MS = 5000;

function timerOf(override: Partial<RoomTimerResponse> = {}): RoomTimerResponse {
  return {
    status: 'IN_PROGRESS',
    timeLimitSec: 1200,
    elapsedActiveMs: 0,
    remainingMs: 600_000,
    awaitingGrading: false,
    serverTime: '2026-11-06T10:00:00',
    ...override,
  };
}

function networkError() {
  return new ApiError({
    status: null,
    message: '서버에 연결할 수 없습니다.',
    code: null,
    detail: null,
    isNetworkError: true,
  });
}

function renderHeartbeat(connectionId: string | null = 'conn-1') {
  const onTimer = vi.fn();
  const onConnectionReplaced = vi.fn();
  const view = renderHook(
    ({ id }) =>
      useHeartbeat({
        roomId: 1,
        connectionId: id,
        intervalMs: INTERVAL_MS,
        onTimer,
        onConnectionReplaced,
      }),
    { initialProps: { id: connectionId } },
  );
  return { ...view, onTimer, onConnectionReplaced };
}

async function advance(ms: number) {
  await act(() => vi.advanceTimersByTimeAsync(ms));
}

beforeEach(() => {
  vi.useFakeTimers();
  heartbeatMock.mockReset();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('useHeartbeat', () => {
  it('연결이 있으면 주기마다 보내고 응답 타이머를 알린다', async () => {
    // given
    heartbeatMock.mockResolvedValue(timerOf());
    const { onTimer } = renderHeartbeat();

    // when
    await advance(INTERVAL_MS * 3);

    // then
    expect(heartbeatMock).toHaveBeenCalledTimes(3);
    expect(onTimer).toHaveBeenCalledTimes(3);
  });

  it('연결이 없으면 보내지 않는다', async () => {
    // given
    heartbeatMock.mockResolvedValue(timerOf());
    renderHeartbeat(null);

    // when
    await advance(INTERVAL_MS * 3);

    // then
    expect(heartbeatMock).not.toHaveBeenCalled();
  });

  it('앞 요청이 끝나기 전에는 다음 요청을 보내지 않는다', async () => {
    // given
    let resolveFirst: (timer: RoomTimerResponse) => void = () => {};
    heartbeatMock.mockImplementationOnce(
      () => new Promise<RoomTimerResponse>((resolve) => (resolveFirst = resolve)),
    );
    heartbeatMock.mockResolvedValue(timerOf());
    renderHeartbeat();

    // when
    await advance(INTERVAL_MS * 4);

    // then
    expect(heartbeatMock).toHaveBeenCalledTimes(1);

    // when 첫 요청이 끝나면 그때부터 다음 주기를 센다
    await act(async () => resolveFirst(timerOf()));
    await advance(INTERVAL_MS);

    // then
    expect(heartbeatMock).toHaveBeenCalledTimes(2);
  });

  it('PAUSED 응답도 그대로 알린다 (화면이 이어하기로 전환한다)', async () => {
    // given
    heartbeatMock.mockResolvedValue(timerOf({ status: 'PAUSED' }));
    const { onTimer } = renderHeartbeat();

    // when
    await advance(INTERVAL_MS);

    // then
    expect(onTimer).toHaveBeenCalledWith(expect.objectContaining({ status: 'PAUSED' }));
  });

  it('409 CONNECTION_REPLACED면 알리고 더 보내지 않는다', async () => {
    // given
    heartbeatMock.mockRejectedValue(
      new ApiError({
        status: 409,
        message: '다른 화면에서 수사를 이어하고 있습니다.',
        code: 'CONNECTION_REPLACED',
        detail: null,
        isNetworkError: false,
      }),
    );
    const { onConnectionReplaced } = renderHeartbeat();

    // when
    await advance(INTERVAL_MS * 3);

    // then
    expect(onConnectionReplaced).toHaveBeenCalledTimes(1);
    expect(heartbeatMock).toHaveBeenCalledTimes(1);
  });

  it('네트워크 오류면 멈추지 않고 다시 보내며, 연속 실패하면 불안정으로 표시한다', async () => {
    // given
    heartbeatMock.mockRejectedValueOnce(networkError());
    heartbeatMock.mockRejectedValueOnce(networkError());
    heartbeatMock.mockResolvedValue(timerOf());
    const { result } = renderHeartbeat();

    // when
    await advance(INTERVAL_MS);

    // then 한 번 실패는 아직 표시하지 않는다
    expect(result.current.isUnstable).toBe(false);

    // when
    await advance(INTERVAL_MS);

    // then
    expect(result.current.isUnstable).toBe(true);

    // when 다시 성공하면 표시를 지운다
    await advance(INTERVAL_MS);

    // then
    expect(heartbeatMock).toHaveBeenCalledTimes(3);
    expect(result.current.isUnstable).toBe(false);
  });

  it('언마운트하면 멈춘다', async () => {
    // given
    heartbeatMock.mockResolvedValue(timerOf());
    const { unmount } = renderHeartbeat();
    await advance(INTERVAL_MS);

    // when
    unmount();
    await advance(INTERVAL_MS * 3);

    // then
    expect(heartbeatMock).toHaveBeenCalledTimes(1);
  });

  it('연결이 지워지면 멈춘다', async () => {
    // given
    heartbeatMock.mockResolvedValue(timerOf());
    const { rerender } = renderHeartbeat();

    // when
    rerender({ id: null });
    await advance(INTERVAL_MS * 3);

    // then
    expect(heartbeatMock).not.toHaveBeenCalled();
  });
});
