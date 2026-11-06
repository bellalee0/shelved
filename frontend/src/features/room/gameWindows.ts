// 게임 화면의 열린 창 목록 (frontend.md "조작·창 관리 규칙"). 창을 여닫는 일은 모두 이 리듀서를 거친다.
// 오른쪽 패널은 한 번에 하나이고, 노트 창은 오른쪽 패널과 동시에 열 수 있다.

export type RightPanel =
  | { kind: 'EVIDENCE'; evidenceId: number }
  | { kind: 'NPC'; suspectId: number }
  | { kind: 'BRIEFING' }
  | { kind: 'HINT' };

export type GameWindowId = 'PANEL' | 'NOTE';

export type GameWindowsState = {
  panel: RightPanel | null;
  /** 패널을 열 때마다 올라가는 번호. 같은 물증을 다시 조사하면 패널을 새로 그리는 key로 쓴다 */
  panelOpenCount: number;
  noteOpen: boolean;
  /** 연 순서. 마지막이 가장 최근에 연 창이다 (Esc가 닫을 대상) */
  openOrder: GameWindowId[];
};

export type GameWindowsAction =
  | { type: 'OPEN_PANEL'; panel: RightPanel }
  | { type: 'CLOSE_PANEL' }
  | { type: 'OPEN_NOTE' }
  | { type: 'CLOSE_NOTE' }
  | { type: 'CLOSE_LATEST' };

export const INITIAL_GAME_WINDOWS: GameWindowsState = {
  panel: null,
  panelOpenCount: 0,
  noteOpen: false,
  openOrder: [],
};

function moveToTop(order: GameWindowId[], id: GameWindowId) {
  return [...order.filter((item) => item !== id), id];
}

function remove(order: GameWindowId[], id: GameWindowId) {
  return order.filter((item) => item !== id);
}

function closeWindow(state: GameWindowsState, id: GameWindowId): GameWindowsState {
  if (id === 'PANEL') {
    return { ...state, panel: null, openOrder: remove(state.openOrder, 'PANEL') };
  }
  return { ...state, noteOpen: false, openOrder: remove(state.openOrder, 'NOTE') };
}

export function gameWindowsReducer(
  state: GameWindowsState,
  action: GameWindowsAction,
): GameWindowsState {
  switch (action.type) {
    case 'OPEN_PANEL':
      // 다른 패널이 열려 있으면 바꾼다. 바꾼 패널이 가장 최근 창이 된다
      return {
        ...state,
        panel: action.panel,
        panelOpenCount: state.panelOpenCount + 1,
        openOrder: moveToTop(state.openOrder, 'PANEL'),
      };
    case 'CLOSE_PANEL':
      return closeWindow(state, 'PANEL');
    case 'OPEN_NOTE':
      return { ...state, noteOpen: true, openOrder: moveToTop(state.openOrder, 'NOTE') };
    case 'CLOSE_NOTE':
      return closeWindow(state, 'NOTE');
    case 'CLOSE_LATEST': {
      const latest = state.openOrder.at(-1);
      return latest ? closeWindow(state, latest) : state;
    }
  }
}

export function hasOpenGameWindow(state: GameWindowsState): boolean {
  return state.openOrder.length > 0;
}
