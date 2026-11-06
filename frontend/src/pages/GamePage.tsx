import { lazy, Suspense, useCallback } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useAuth } from '../features/auth/hooks/useAuth';
import { EvidencePanel } from '../features/evidence/components/EvidencePanel';
import { NpcPanel } from '../features/npc/components/NpcPanel';
import { BriefingPanel } from '../features/room/components/BriefingPanel';
import { ExitDialog } from '../features/room/components/ExitDialog';
import { GameHud } from '../features/room/components/GameHud';
import { SessionOverlay } from '../features/room/components/SessionOverlay';
import type { RightPanel } from '../features/room/gameWindows';
import { useGameSession } from '../features/room/hooks/useGameSession';
import { useGameWindows } from '../features/room/hooks/useGameWindows';
import { useInteractRequests } from '../features/room/hooks/useInteractRequests';
import { useMapInputSync } from '../features/room/hooks/useMapInputSync';
import { useMapLoadStatus } from '../features/room/hooks/useMapLoadStatus';
import { type NoteFlushOutcome, useSaveAndExit } from '../features/room/hooks/useSaveAndExit';
import { canMoveOnMap } from '../features/room/inputPermission';
import type { RoomBriefingResponse, RoomMapResponse } from '../features/room/types';

// Phaser는 용량이 커서 게임 화면에 들어올 때만 따로 내려받는다
const PhaserGame = lazy(() =>
  import('../game/PhaserGame').then((module) => ({ default: module.PhaserGame })),
);

function parseRoomId(value: string | undefined) {
  const roomId = Number(value);
  return Number.isInteger(roomId) && roomId > 0 ? roomId : null;
}

// 개인 노트(FE-6) 연결 전 자리. 연결하면 useNote의 flush·hasUnsavedDraft로 바꾼다
const flushNotePlaceholder = async (): Promise<NoteFlushOutcome> => 'NO_CHANGES';
const HAS_UNSAVED_DRAFT_PLACEHOLDER = false;

/** 게임 화면(/rooms/:roomId). 주소의 roomId를 확인한 뒤 실제 화면을 조립한다 */
export function GamePage() {
  const roomId = parseRoomId(useParams().roomId);
  const { user } = useAuth();

  if (roomId === null || !user) {
    return (
      <main className="mx-auto max-w-xl px-6 py-12">
        <p role="alert" className="text-danger">
          올바르지 않은 수사 주소입니다.
        </p>
        <Link to="/" className="text-sm text-accent underline">
          메인으로
        </Link>
      </main>
    );
  }
  // 다른 세션·사용자로 바뀌면 화면 상태를 모두 새로 시작한다
  return <GameScreen key={`${user.userId}-${roomId}`} roomId={roomId} userId={user.userId} />;
}

type GameScreenProps = {
  roomId: number;
  userId: number;
};

/**
 * 게임 화면 조립: 위쪽 HUD, 가운데 맵, 오른쪽 패널(한 번에 하나), 전체를 덮는 흐름 오버레이.
 * 상태·로직은 features/room(세션·창·입력 허용)과 각 패널의 도메인에 있고, 여기서는 연결만 한다.
 */
function GameScreen({ roomId, userId }: GameScreenProps) {
  const navigate = useNavigate();
  const session = useGameSession(roomId, userId);
  const map = useMapLoadStatus();
  const hasOverlay = session.phase !== 'playing';
  const windows = useGameWindows(hasOverlay);

  const { disconnect } = session;
  const handleLeave = useCallback(() => {
    disconnect();
    navigate('/');
  }, [disconnect, navigate]);
  const exit = useSaveAndExit({ roomId, flushNote: flushNotePlaceholder, onLeave: handleLeave });

  const conditions = {
    mapReady: map.status === 'ready',
    hasConnection: session.hasConnection,
    connectionReplaced: session.phase === 'replaced',
    // 나가기 진행 창도 열린 창으로 본다 (그동안 이동하지 않게)
    hasOpenWindow: windows.hasOpenWindow || exit.step !== 'idle',
    hasOverlay,
    status: session.roomState?.timer.status ?? null,
    displayRemainingMs: session.displayRemainingMs,
    awaitingGrading: session.roomState?.timer.awaitingGrading ?? false,
  };
  const mapInputEnabled = canMoveOnMap(conditions);
  // 노트 연결 시 canOperateSession(conditions)을 useNote의 operable로 넘긴다

  useMapInputSync(conditions.mapReady, mapInputEnabled);
  useInteractRequests(mapInputEnabled, ({ type, targetId }) => {
    windows.openPanel(
      type === 'EVIDENCE'
        ? { kind: 'EVIDENCE', evidenceId: targetId }
        : { kind: 'NPC', suspectId: targetId },
    );
  });

  // 결과·채점 대기 화면에서는 맵을 그리지 않는다. 재접속 중에는 이미 그린 맵을 그대로 둔다
  const showMap =
    session.mapData !== null && session.phase !== 'ended' && session.phase !== 'awaitingGrading';

  return (
    <div className="flex h-dvh flex-col">
      {session.roomState && (
        <GameHud
          roomState={session.roomState}
          displayRemainingMs={session.displayRemainingMs}
          isConnectionUnstable={session.isConnectionUnstable}
          onOpenBriefing={() => windows.openPanel({ kind: 'BRIEFING' })}
          onExit={() => void exit.start()}
          actionsEnabled={session.phase === 'playing' && exit.step === 'idle'}
        />
      )}
      <div className="flex min-h-0 flex-1">
        <div className="relative min-w-0 flex-1 bg-ink">
          {showMap && session.mapData && (
            <Suspense fallback={null}>
              <PhaserGame key={map.gameKey} mapData={session.mapData} />
            </Suspense>
          )}
        </div>
        {windows.panel && (
          <aside className="w-96 shrink-0">
            <RightPanelContent
              key={windows.panelOpenCount}
              panel={windows.panel}
              roomId={roomId}
              briefing={session.briefing}
              mapData={session.mapData}
              onSessionRejected={session.handleSessionRejected}
              onClose={windows.closePanel}
            />
          </aside>
        )}
      </div>
      <SessionOverlay
        phase={session.phase}
        enterError={session.enterError}
        roomState={session.roomState}
        briefing={session.briefing}
        mapStatus={map.status}
        actionError={session.actionError}
        isSubmitting={session.isSubmitting}
        hasUnsavedDraft={HAS_UNSAVED_DRAFT_PLACEHOLDER}
        onStart={() => void session.start()}
        onResume={() => void session.resume()}
        onReenter={session.reenter}
        onCheckState={() => void session.checkState()}
        onRetryMap={map.retry}
      />
      <ExitDialog
        step={exit.step}
        errorMessage={exit.errorMessage}
        onRetryNote={() => void exit.start()}
        onLeaveWithoutNote={() => void exit.leaveWithoutNote()}
        onRetryPause={() => void exit.retryPause()}
        onCancel={exit.cancel}
      />
    </div>
  );
}

type RightPanelContentProps = {
  panel: RightPanel;
  roomId: number;
  briefing: RoomBriefingResponse | null;
  mapData: RoomMapResponse | null;
  onSessionRejected: (code: string) => void;
  onClose: () => void;
};

/** 열린 오른쪽 패널 하나를 그린다 */
function RightPanelContent({
  panel,
  roomId,
  briefing,
  mapData,
  onSessionRejected,
  onClose,
}: RightPanelContentProps) {
  switch (panel.kind) {
    case 'EVIDENCE':
      return (
        <EvidencePanel
          roomId={roomId}
          evidenceId={panel.evidenceId}
          onClose={onClose}
          onSessionRejected={onSessionRejected}
        />
      );
    case 'NPC': {
      const suspect = mapData?.suspects.find((item) => item.suspectId === panel.suspectId);
      return <NpcPanel suspectName={suspect?.name ?? '인물'} onClose={onClose} />;
    }
    case 'BRIEFING':
      return briefing ? <BriefingPanel briefing={briefing} onClose={onClose} /> : null;
    case 'HINT':
      // 힌트 대화창은 3주차 작업에서 연결한다
      return null;
  }
}
