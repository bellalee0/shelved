import { useEffect, useRef } from 'react';
import { EventBus } from '../../../game/EventBus';
import { GameEvents, type GameEventPayloads } from '../../../game/events';

type InteractPayload = GameEventPayloads[typeof GameEvents.INTERACT_REQUESTED];

/**
 * Phaser의 INTERACT_REQUESTED(E키로 고른 NPC·물증)를 받는다.
 * enabled가 false면 무시한다 — 입력을 막은 직후 이미 큐에 있던 이벤트가 창을 열지 않게 한다.
 */
export function useInteractRequests(
  enabled: boolean,
  onInteract: (payload: InteractPayload) => void,
) {
  const latestRef = useRef({ enabled, onInteract });
  useEffect(() => {
    latestRef.current = { enabled, onInteract };
  });

  useEffect(() => {
    const handleInteract = (payload: InteractPayload) => {
      if (latestRef.current.enabled) {
        latestRef.current.onInteract(payload);
      }
    };
    EventBus.on(GameEvents.INTERACT_REQUESTED, handleInteract);
    return () => EventBus.off(GameEvents.INTERACT_REQUESTED, handleInteract);
  }, []);
}
