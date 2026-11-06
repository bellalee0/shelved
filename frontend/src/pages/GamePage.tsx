// FE-5에서 교체하는 개발 확인용 화면.
// 맵을 눈으로 확인하는 용도로, 세션 상태 검사·시작·하트비트·HUD·패널은 없다.
// MAP_READY를 받으면 바로 입력을 허용하고, E키로 받은 INTERACT_REQUESTED를 한 줄로 보여 준다.
import { lazy, Suspense, useEffect, useState } from 'react';
import { Link, useParams } from 'react-router';
import { useAuth } from '../features/auth/hooks/useAuth';
import { useRoomMap } from '../features/room/hooks/useRoomMap';
import { EventBus } from '../game/EventBus';
import { GameEvents, type GameEventPayloads } from '../game/events';

// Phaser는 용량이 커서 게임 화면에 들어올 때만 따로 내려받는다
const PhaserGame = lazy(() =>
  import('../game/PhaserGame').then((module) => ({ default: module.PhaserGame })),
);

type MapStatus = 'loading' | 'ready' | 'failed';

function parseRoomId(value: string | undefined) {
  const roomId = Number(value);
  return Number.isInteger(roomId) && roomId > 0 ? roomId : null;
}

/** 게임 화면(/rooms/:roomId). 지금은 맵 확인용 임시 화면이다 */
export function GamePage() {
  const roomId = parseRoomId(useParams().roomId);
  const { user } = useAuth();
  const mapQuery = useRoomMap(roomId, user?.userId ?? null);
  const [mapStatus, setMapStatus] = useState<MapStatus>('loading');
  const [gameKey, setGameKey] = useState(0);
  const [lastInteraction, setLastInteraction] = useState<
    GameEventPayloads[typeof GameEvents.INTERACT_REQUESTED] | null
  >(null);

  useEffect(() => {
    const handleMapReady = () => {
      setMapStatus('ready');
      EventBus.emit(GameEvents.SET_INPUT_ENABLED, { enabled: true });
    };
    const handleMapLoadFailed = () => setMapStatus('failed');
    const handleInteract = (payload: GameEventPayloads[typeof GameEvents.INTERACT_REQUESTED]) =>
      setLastInteraction(payload);

    EventBus.on(GameEvents.MAP_READY, handleMapReady);
    EventBus.on(GameEvents.MAP_LOAD_FAILED, handleMapLoadFailed);
    EventBus.on(GameEvents.INTERACT_REQUESTED, handleInteract);
    return () => {
      EventBus.off(GameEvents.MAP_READY, handleMapReady);
      EventBus.off(GameEvents.MAP_LOAD_FAILED, handleMapLoadFailed);
      EventBus.off(GameEvents.INTERACT_REQUESTED, handleInteract);
    };
  }, []);

  const handleRetry = () => {
    setMapStatus('loading');
    setGameKey((key) => key + 1);
  };

  return (
    <main className="mx-auto max-w-5xl px-6 py-6">
      <div className="flex items-center justify-between">
        <h1 className="text-xl font-bold">수사 화면</h1>
        <Link to="/" className="text-sm text-accent underline">
          메인으로
        </Link>
      </div>

      {mapQuery.isError ? (
        <p role="alert" className="mt-6 text-danger">
          맵 정보를 불러오지 못했습니다.
        </p>
      ) : null}

      {mapQuery.data ? (
        <div className="relative mt-4 aspect-[3/2] w-full overflow-hidden bg-ink">
          <Suspense fallback={null}>
            <PhaserGame key={gameKey} mapData={mapQuery.data} />
          </Suspense>
          {mapStatus === 'loading' ? (
            <p className="absolute inset-0 flex items-center justify-center text-paper">
              맵을 불러오는 중…
            </p>
          ) : null}
          {mapStatus === 'failed' ? (
            <div
              role="alert"
              className="absolute inset-0 flex flex-col items-center justify-center gap-3 bg-ink text-paper"
            >
              <p>맵을 불러오지 못했습니다.</p>
              <button type="button" className="underline" onClick={handleRetry}>
                다시 시도
              </button>
            </div>
          ) : null}
        </div>
      ) : null}

      {mapQuery.isPending && roomId !== null ? (
        <p className="mt-6 text-muted">맵 정보를 불러오는 중…</p>
      ) : null}

      <p className="mt-3 text-sm text-muted" data-testid="last-interaction">
        {lastInteraction
          ? `마지막 상호작용: ${lastInteraction.type} #${lastInteraction.targetId}`
          : '마지막 상호작용: 없음'}
      </p>
    </main>
  );
}
