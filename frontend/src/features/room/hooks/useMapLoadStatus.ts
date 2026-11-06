import { useCallback, useEffect, useState } from 'react';
import { EventBus } from '../../../game/EventBus';
import { GameEvents } from '../../../game/events';

export type MapLoadStatus = 'loading' | 'ready' | 'failed';

/**
 * Phaser의 MAP_READY·MAP_LOAD_FAILED를 받아 맵 상태를 알려 준다.
 * retry는 gameKey를 올려 PhaserGame을 다시 마운트(게임을 새로 생성)하게 한다.
 */
export function useMapLoadStatus() {
  const [status, setStatus] = useState<MapLoadStatus>('loading');
  const [gameKey, setGameKey] = useState(0);

  useEffect(() => {
    const handleReady = () => setStatus('ready');
    const handleFailed = () => setStatus('failed');
    EventBus.on(GameEvents.MAP_READY, handleReady);
    EventBus.on(GameEvents.MAP_LOAD_FAILED, handleFailed);
    return () => {
      EventBus.off(GameEvents.MAP_READY, handleReady);
      EventBus.off(GameEvents.MAP_LOAD_FAILED, handleFailed);
    };
  }, []);

  const retry = useCallback(() => {
    setStatus('loading');
    setGameKey((key) => key + 1);
  }, []);

  return { status, gameKey, retry };
}
