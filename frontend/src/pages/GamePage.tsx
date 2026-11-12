import { lazy, Suspense, useCallback } from 'react';
import { Link, useNavigate, useParams } from 'react-router';
import { useAuth } from '../features/auth/hooks/useAuth';
import { EvidencePanel } from '../features/evidence/components/EvidencePanel';
import { NoteWindow, useNote } from '../features/note';
import { ConversationLogPanel, joinConversations, NpcPanel } from '../features/npc';
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
import { useSaveAndExit } from '../features/room/hooks/useSaveAndExit';
import { canMoveOnMap, canOperateSession } from '../features/room/inputPermission';
import type {
  RoomBriefingResponse,
  RoomMapResponse,
  RoomStateResponse,
} from '../features/room/types';

// Phaser는 용량이 커서 게임 화면에 들어올 때만 따로 내려받는다
const PhaserGame = lazy(() =>
  import('../game/PhaserGame').then((module) => ({ default: module.PhaserGame })),
);

function parseRoomId(value: string | undefined) {
  const roomId = Number(value);
  return Number.isInteger(roomId) && roomId > 0 ? roomId : null;
}

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

  const { disconnect, checkState } = session;
  const handleLeave = useCallback(() => {
    disconnect();
    navigate('/');
  }, [disconnect, navigate]);

  // NPC 답변 결과(최초 성공 당시 스냅샷)로 현재 상태를 덮지 않고, GET state를 다시 조회해 대화 목록·시간을 맞춘다.
  // 진행 중이 아니면 입장·재접속 흐름이 곧 state를 다시 받으므로 따로 조회하지 않는다
  const sessionPhase = session.phase;
  const handleNpcAnswered = useCallback(() => {
    if (sessionPhase === 'playing') {
      void checkState();
    }
  }, [sessionPhase, checkState]);

  const baseConditions = {
    mapReady: map.status === 'ready',
    hasConnection: session.hasConnection,
    connectionReplaced: session.phase === 'replaced',
    hasOpenWindow: windows.hasOpenWindow,
    hasOverlay,
    status: session.roomState?.timer.status ?? null,
    displayRemainingMs: session.displayRemainingMs,
    awaitingGrading: session.roomState?.timer.awaitingGrading ?? false,
  };
  // 노트는 창이 열려 있어도 저장할 수 있어야 하므로 맵 준비·열린 창과 무관한 canOperateSession을 쓴다.
  // 노트 창이 닫혀 있어도 flush가 동작하도록 항상 호출한다
  const operable = canOperateSession(baseConditions);
  const note = useNote({
    roomId,
    userId,
    operable,
    // 연결 교체면 교체 안내, 그 외(진행 중 아님·시간 만료)는 상태를 다시 조회해 분기한다
    onSessionRejected: session.handleSessionRejected,
    // 재접속하면 다른 화면이 저장했을 수 있는 최신 노트를 다시 받는다
    resyncKey: session.reconnectKey,
  });
  const exit = useSaveAndExit({ roomId, flushNote: note.flush, onLeave: handleLeave });
  const mapInputEnabled = canMoveOnMap({
    ...baseConditions,
    // 나가기 진행 창도 열린 창으로 본다 (그동안 이동하지 않게)
    hasOpenWindow: baseConditions.hasOpenWindow || exit.step !== 'idle',
  });

  useMapInputSync(baseConditions.mapReady, mapInputEnabled);
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
          onOpenNote={windows.openNote}
          onOpenBriefing={() => windows.openPanel({ kind: 'BRIEFING' })}
          onOpenConversations={() => windows.openPanel({ kind: 'CONVERSATIONS' })}
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
          {/* 노트 창은 맵 위에 떠서 오른쪽 패널과 동시에 열 수 있다 */}
          {windows.noteOpen && (
            <div className="absolute top-4 left-4 z-10 max-h-[calc(100%-2rem)] overflow-y-auto">
              <NoteWindow note={note} onClose={windows.closeNote} />
            </div>
          )}
          {/* 오른쪽 패널도 맵 위에 떠서, 열고 닫아도 맵 영역 크기가 변하지 않는다(맵이 움직이지 않는다) */}
          {windows.panel && (
            <aside className="absolute top-0 right-0 z-10 h-full w-96">
              <RightPanelContent
                key={windows.panelOpenCount}
                panel={windows.panel}
                roomId={roomId}
                briefing={session.briefing}
                mapData={session.mapData}
                roomState={session.roomState}
                operable={operable}
                reconnectKey={session.reconnectKey}
                onNpcAnswered={handleNpcAnswered}
                onSessionRejected={session.handleSessionRejected}
                onClose={windows.closePanel}
              />
            </aside>
          )}
        </div>
      </div>
      <SessionOverlay
        phase={session.phase}
        enterError={session.enterError}
        roomState={session.roomState}
        briefing={session.briefing}
        mapStatus={map.status}
        actionError={session.actionError}
        isSubmitting={session.isSubmitting}
        hasUnsavedDraft={note.hasUnsavedDraft}
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
  roomState: RoomStateResponse | null;
  /** 세션 조작(질문 전송)을 보낼 수 있는지. 맵 준비·열린 창과 무관하다 */
  operable: boolean;
  reconnectKey: number;
  onNpcAnswered: () => void;
  onSessionRejected: (code: string) => void;
  onClose: () => void;
};

/** 열린 오른쪽 패널 하나를 그린다 */
function RightPanelContent({
  panel,
  roomId,
  briefing,
  mapData,
  roomState,
  operable,
  reconnectKey,
  onNpcAnswered,
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
      return (
        <NpcPanel
          roomId={roomId}
          suspect={{
            suspectId: panel.suspectId,
            name: suspect?.name ?? '인물',
            // 맵 정보에 없는 인물이면 서버도 거부하므로 미리 막는다
            canConverse: suspect?.canConverse ?? false,
          }}
          operable={operable}
          resyncKey={reconnectKey}
          onAnswered={onNpcAnswered}
          onSessionRejected={onSessionRejected}
          onClose={onClose}
        />
      );
    }
    case 'CONVERSATIONS':
      return (
        <ConversationLogPanel
          roomId={roomId}
          conversations={joinConversations(roomState?.conversations ?? [], mapData?.suspects ?? [])}
          onClose={onClose}
        />
      );
    case 'BRIEFING':
      return briefing ? <BriefingPanel briefing={briefing} onClose={onClose} /> : null;
    case 'HINT':
      // 힌트 대화창은 3주차 작업에서 연결한다
      return null;
  }
}
