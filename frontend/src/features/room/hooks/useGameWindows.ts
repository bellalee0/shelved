import { useCallback, useEffect, useReducer } from 'react';
import {
  gameWindowsReducer,
  hasOpenGameWindow,
  INITIAL_GAME_WINDOWS,
  type RightPanel,
} from '../gameWindows';

/**
 * 게임 화면의 열린 창 목록과 Esc 처리.
 * Esc는 가장 최근에 연 창 하나만 닫는다. escapeBlocked(오버레이가 떠 있음)면 Esc로 아무것도 닫지 않는다.
 * 텍스트 입력 중에도 Esc는 창을 닫는다 (WASD·E는 Phaser가 입력 중에 무시한다).
 */
export function useGameWindows(escapeBlocked: boolean) {
  const [state, dispatch] = useReducer(gameWindowsReducer, INITIAL_GAME_WINDOWS);

  useEffect(() => {
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key !== 'Escape' || escapeBlocked) {
        return;
      }
      dispatch({ type: 'CLOSE_LATEST' });
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [escapeBlocked]);

  const openPanel = useCallback((panel: RightPanel) => dispatch({ type: 'OPEN_PANEL', panel }), []);
  const closePanel = useCallback(() => dispatch({ type: 'CLOSE_PANEL' }), []);
  const openNote = useCallback(() => dispatch({ type: 'OPEN_NOTE' }), []);
  const closeNote = useCallback(() => dispatch({ type: 'CLOSE_NOTE' }), []);

  return {
    panel: state.panel,
    panelOpenCount: state.panelOpenCount,
    noteOpen: state.noteOpen,
    hasOpenWindow: hasOpenGameWindow(state),
    openPanel,
    closePanel,
    openNote,
    closeNote,
  };
}
