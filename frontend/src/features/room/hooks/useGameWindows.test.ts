import { act, fireEvent, renderHook } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { useGameWindows } from './useGameWindows';

function pressEscape() {
  act(() => {
    fireEvent.keyDown(window, { key: 'Escape' });
  });
}

describe('useGameWindows', () => {
  it('Esc는 가장 최근에 연 창 하나만 닫는다', () => {
    // given
    const { result } = renderHook(() => useGameWindows(false));
    act(() => {
      result.current.openPanel({ kind: 'BRIEFING' });
      result.current.openNote();
    });

    // when
    pressEscape();

    // then
    expect(result.current.noteOpen).toBe(false);
    expect(result.current.panel).toEqual({ kind: 'BRIEFING' });
  });

  it('오버레이가 떠 있으면 Esc로 아무것도 닫지 않는다', () => {
    // given
    const { result, rerender } = renderHook(({ blocked }) => useGameWindows(blocked), {
      initialProps: { blocked: false },
    });
    act(() => result.current.openPanel({ kind: 'BRIEFING' }));
    rerender({ blocked: true });

    // when
    pressEscape();

    // then
    expect(result.current.panel).toEqual({ kind: 'BRIEFING' });
    expect(result.current.hasOpenWindow).toBe(true);
  });

  it('언마운트하면 Esc 구독을 해제한다', () => {
    // given
    const { result, unmount } = renderHook(() => useGameWindows(false));
    act(() => result.current.openPanel({ kind: 'BRIEFING' }));
    const before = result.current;

    // when
    unmount();
    fireEvent.keyDown(window, { key: 'Escape' });

    // then
    expect(result.current).toBe(before);
  });
});
