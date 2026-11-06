import { useEffect, useRef } from 'react';
import type { RoomMapResponse } from '../features/room/types';
import { disconnectGame } from './bridge';
import { createGame } from './createGame';

export type PhaserGameProps = {
  /** GET /api/rooms/{roomId}/map 응답. 값(참조)이 바뀌면 게임을 새로 만든다 */
  mapData: RoomMapResponse;
};

/**
 * Phaser 게임을 React 화면에 붙이는 연결 컴포넌트.
 * 마운트 시 게임을 만들고, 언마운트·맵 교체 시 EventBus 구독을 끊은 뒤 게임을 없앤다.
 * React는 이 컴포넌트 밖에서 EventBus로만 Phaser와 대화한다 (Scene 객체를 직접 만지지 않는다).
 * Phaser가 커서 메인 화면 번들에 들어가지 않도록, 사용하는 쪽에서 React.lazy로 불러온다.
 */
export function PhaserGame({ mapData }: PhaserGameProps) {
  const containerRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const container = containerRef.current;
    if (!container) {
      return;
    }
    // StrictMode(개발 모드)는 마운트→정리→마운트를 한 번 더 하므로, 정리에서 확실히 끊어 게임이 하나만 남게 한다
    const game = createGame(container, mapData);
    return () => {
      disconnectGame(game);
      game.destroy(true);
    };
  }, [mapData]);

  return <div ref={containerRef} className="h-full w-full" data-testid="phaser-game" />;
}
