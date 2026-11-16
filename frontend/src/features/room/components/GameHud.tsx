import { Button } from '../../../shared/components/Button';
import { DIFFICULTY_LABELS, ROOM_STATUS_LABELS } from '../../../shared/constants/labels';
import { formatRemainingTime } from '../../../shared/utils/formatTime';
import { formatUsage } from '../formatUsage';
import { toAiRequestBadges } from '../aiRequestBadges';
import type { RoomStateResponse } from '../types';

type GameHudProps = {
  roomState: RoomStateResponse;
  displayRemainingMs: number | null;
  isConnectionUnstable: boolean;
  /** 없으면 "노트" 버튼을 비활성으로 둔다 */
  onOpenNote?: () => void;
  onOpenBriefing: () => void;
  onOpenConversations: () => void;
  onOpenHint: () => void;
  onExit: () => void;
  /** 진행 중일 때만 버튼을 누를 수 있다 */
  actionsEnabled: boolean;
};

/** 처리 중·다시 보내기 대기 요청이 있다는 작은 점 */
function PendingBadge() {
  return (
    <span
      role="img"
      aria-label="처리 중인 요청 있음"
      className="ml-1.5 inline-block size-2 rounded-full bg-accent align-middle"
    />
  );
}

/** 게임 화면 위쪽 HUD. 사건 정보·타이머·남은 횟수는 항상 보인다 */
export function GameHud({
  roomState,
  displayRemainingMs,
  isConnectionUnstable,
  onOpenNote,
  onOpenBriefing,
  onOpenConversations,
  onOpenHint,
  onExit,
  actionsEnabled,
}: GameHudProps) {
  // 재접속 직후 처리 중·재시도 대기 요청이 있으면 해당 버튼에 점을 보여 창을 열어 보게 한다
  const badges = toAiRequestBadges(roomState.aiRequests);
  return (
    <header className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-line bg-surface px-5 py-3">
      <div className="flex min-w-0 items-center gap-2">
        <h1 className="truncate font-bold">{roomState.displayTitle}</h1>
        <span className="text-sm text-muted">{DIFFICULTY_LABELS[roomState.difficulty]}</span>
        <span className="rounded-full border border-line px-2 py-0.5 text-xs">
          {ROOM_STATUS_LABELS[roomState.timer.status]}
        </span>
      </div>
      <p className="font-mono text-lg tabular-nums" aria-label="남은 시간">
        {formatRemainingTime(displayRemainingMs ?? roomState.timer.remainingMs)}
      </p>
      <p className="text-sm">{formatUsage('힌트', roomState.hintCount)}</p>
      <p className="text-sm">{formatUsage('지목', roomState.accusationCount)}</p>
      {isConnectionUnstable && (
        <p role="status" className="text-sm text-danger">
          연결이 불안정합니다
        </p>
      )}
      <div className="ml-auto flex flex-wrap gap-2">
        <Button variant="secondary" onClick={onOpenNote} disabled={!actionsEnabled || !onOpenNote}>
          노트
        </Button>
        <Button variant="secondary" onClick={onOpenBriefing} disabled={!actionsEnabled}>
          사전 정보
        </Button>
        <Button variant="secondary" onClick={onOpenConversations} disabled={!actionsEnabled}>
          대화 기록
          {badges.npc && <PendingBadge />}
        </Button>
        <Button variant="secondary" onClick={onOpenHint} disabled={!actionsEnabled}>
          힌트
          {badges.hint && <PendingBadge />}
        </Button>
        {/* 지목은 이후 작업에서 연결한다 */}
        <Button variant="secondary" disabled>
          지목
        </Button>
        <Button variant="ghost" onClick={onExit} disabled={!actionsEnabled}>
          저장 후 나가기
        </Button>
      </div>
    </header>
  );
}
