import { useEffect } from 'react';
import { EventBus } from '../../../game/EventBus';
import { GameEvents } from '../../../game/events';

/**
 * 맵 입력 허용 값을 Phaser에 알린다. 값이 바뀔 때마다 SET_INPUT_ENABLED를 보낸다.
 * MAP_READY 전에는 Scene이 아직 구독하지 않으므로 보내지 않고, 준비되는 순간 현재 값을 보낸다.
 */
export function useMapInputSync(mapReady: boolean, enabled: boolean) {
  useEffect(() => {
    if (!mapReady) {
      return;
    }
    EventBus.emit(GameEvents.SET_INPUT_ENABLED, { enabled });
  }, [mapReady, enabled]);
}
