import { act, renderHook } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../../shared/api/apiError';
import { pauseRoom } from '../api';
import type { RoomTimerResponse } from '../types';
import { type NoteFlushOutcome, useSaveAndExit } from './useSaveAndExit';

vi.mock('../api', () => ({ pauseRoom: vi.fn() }));

const pauseMock = vi.mocked(pauseRoom);

function timerOf(status: RoomTimerResponse['status']): RoomTimerResponse {
  return {
    status,
    timeLimitSec: 1200,
    elapsedActiveMs: 0,
    remainingMs: 600_000,
    awaitingGrading: false,
    serverTime: '2026-11-06T10:00:00',
  };
}

function renderExit(flushResults: NoteFlushOutcome[]) {
  const calls: string[] = [];
  const flushNote = vi.fn(async () => {
    calls.push('flush');
    return flushResults.shift() ?? 'NO_CHANGES';
  });
  const onLeave = vi.fn(() => calls.push('leave'));
  pauseMock.mockImplementation(async () => {
    calls.push('pause');
    return timerOf('PAUSED');
  });
  const view = renderHook(() => useSaveAndExit({ roomId: 1, flushNote, onLeave }));
  return { ...view, calls, flushNote, onLeave };
}

beforeEach(() => {
  pauseMock.mockReset();
});

describe('useSaveAndExit', () => {
  it('노트 저장 → pause → 떠나기 순서로 진행한다', async () => {
    // given
    const { result, calls } = renderExit(['SAVED']);

    // when
    await act(() => result.current.start());

    // then
    expect(calls).toEqual(['flush', 'pause', 'leave']);
    expect(result.current.step).toBe('idle');
  });

  it('노트 저장에 실패하면 멈추고, 다시 시도하면 처음부터 진행한다', async () => {
    // given
    const { result, calls } = renderExit(['FAILED', 'SAVED']);

    // when
    await act(() => result.current.start());

    // then
    expect(result.current.step).toBe('flushFailed');
    expect(calls).toEqual(['flush']);

    // when
    await act(() => result.current.start());

    // then
    expect(calls).toEqual(['flush', 'flush', 'pause', 'leave']);
  });

  it('노트 저장 실패 후 "그래도 나가기"는 노트 없이 pause부터 한다', async () => {
    // given
    const { result, calls } = renderExit(['FAILED']);
    await act(() => result.current.start());

    // when
    await act(() => result.current.leaveWithoutNote());

    // then
    expect(calls).toEqual(['flush', 'pause', 'leave']);
  });

  it('pause가 실패하면 떠나지 않고, 다시 시도할 수 있다', async () => {
    // given
    const { result, calls, onLeave } = renderExit(['NO_CHANGES']);
    pauseMock.mockRejectedValueOnce(
      new ApiError({
        status: null,
        message: '서버에 연결할 수 없습니다.',
        code: null,
        detail: null,
        isNetworkError: true,
      }),
    );

    // when
    await act(() => result.current.start());

    // then
    expect(result.current.step).toBe('pauseFailed');
    expect(result.current.errorMessage).toBe('서버에 연결할 수 없습니다.');
    expect(onLeave).not.toHaveBeenCalled();

    // when
    await act(() => result.current.retryPause());

    // then
    expect(calls).toEqual(['flush', 'pause', 'leave']);
  });

  it('pause 응답이 PAUSED가 아니면 떠나지 않는다', async () => {
    // given
    const { result, onLeave } = renderExit(['NO_CHANGES']);
    pauseMock.mockResolvedValueOnce(timerOf('IN_PROGRESS'));

    // when
    await act(() => result.current.start());

    // then
    expect(result.current.step).toBe('pauseFailed');
    expect(onLeave).not.toHaveBeenCalled();
  });

  it('pause 응답이 종료 상태여도 떠난다', async () => {
    // given
    const { result, onLeave } = renderExit(['NO_CHANGES']);
    pauseMock.mockResolvedValueOnce(timerOf('EXPIRED'));

    // when
    await act(() => result.current.start());

    // then
    expect(onLeave).toHaveBeenCalledTimes(1);
  });
});
