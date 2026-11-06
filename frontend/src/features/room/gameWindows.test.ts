import { describe, expect, it } from 'vitest';
import {
  gameWindowsReducer,
  hasOpenGameWindow,
  INITIAL_GAME_WINDOWS,
  type GameWindowsAction,
} from './gameWindows';

function reduce(actions: GameWindowsAction[]) {
  return actions.reduce(gameWindowsReducer, INITIAL_GAME_WINDOWS);
}

describe('gameWindowsReducer', () => {
  it('오른쪽 패널은 하나만 열리고, 다른 패널을 열면 바뀐다', () => {
    // given
    const actions: GameWindowsAction[] = [
      { type: 'OPEN_PANEL', panel: { kind: 'BRIEFING' } },
      { type: 'OPEN_PANEL', panel: { kind: 'EVIDENCE', evidenceId: 2 } },
    ];

    // when
    const state = reduce(actions);

    // then
    expect(state.panel).toEqual({ kind: 'EVIDENCE', evidenceId: 2 });
    expect(state.openOrder).toEqual(['PANEL']);
  });

  it('노트 창은 오른쪽 패널과 동시에 열 수 있다', () => {
    // given
    const actions: GameWindowsAction[] = [
      { type: 'OPEN_PANEL', panel: { kind: 'BRIEFING' } },
      { type: 'OPEN_NOTE' },
    ];

    // when
    const state = reduce(actions);

    // then
    expect(state.panel).toEqual({ kind: 'BRIEFING' });
    expect(state.noteOpen).toBe(true);
    expect(hasOpenGameWindow(state)).toBe(true);
  });

  it('CLOSE_LATEST는 가장 최근에 연 창 하나만 닫는다', () => {
    // given
    const opened = reduce([
      { type: 'OPEN_NOTE' },
      { type: 'OPEN_PANEL', panel: { kind: 'BRIEFING' } },
    ]);

    // when
    const once = gameWindowsReducer(opened, { type: 'CLOSE_LATEST' });
    const twice = gameWindowsReducer(once, { type: 'CLOSE_LATEST' });

    // then
    expect(once.panel).toBeNull();
    expect(once.noteOpen).toBe(true);
    expect(twice.noteOpen).toBe(false);
    expect(hasOpenGameWindow(twice)).toBe(false);
  });

  it('패널을 바꿔 열면 그 패널이 가장 최근 창이 된다', () => {
    // given
    const opened = reduce([
      { type: 'OPEN_PANEL', panel: { kind: 'BRIEFING' } },
      { type: 'OPEN_NOTE' },
      { type: 'OPEN_PANEL', panel: { kind: 'NPC', suspectId: 1 } },
    ]);

    // when
    const state = gameWindowsReducer(opened, { type: 'CLOSE_LATEST' });

    // then
    expect(state.panel).toBeNull();
    expect(state.noteOpen).toBe(true);
  });

  it('같은 물증을 다시 열어도 패널 번호가 올라간다 (다시 조사)', () => {
    // given
    const panel = { kind: 'EVIDENCE', evidenceId: 1 } as const;
    const first = reduce([{ type: 'OPEN_PANEL', panel }]);

    // when
    const second = gameWindowsReducer(first, { type: 'OPEN_PANEL', panel });

    // then
    expect(second.panelOpenCount).toBe(first.panelOpenCount + 1);
  });

  it('열린 창이 없을 때 CLOSE_LATEST는 아무것도 바꾸지 않는다', () => {
    // when
    const state = gameWindowsReducer(INITIAL_GAME_WINDOWS, { type: 'CLOSE_LATEST' });

    // then
    expect(state).toBe(INITIAL_GAME_WINDOWS);
  });
});
