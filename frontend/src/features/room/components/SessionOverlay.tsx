import type { ReactNode } from 'react';
import { Link } from 'react-router';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { formatRemainingTime } from '../../../shared/utils/formatTime';
import type { MapLoadStatus } from '../hooks/useMapLoadStatus';
import type { SessionPhase } from '../sessionPhase';
import type { RoomBriefingResponse, RoomStateResponse } from '../types';
import { formatUsage } from '../formatUsage';
import { BriefingContent } from './BriefingContent';

type SessionOverlayProps = {
  phase: SessionPhase;
  enterError: string | null;
  roomState: RoomStateResponse | null;
  briefing: RoomBriefingResponse | null;
  mapStatus: MapLoadStatus;
  actionError: string | null;
  isSubmitting: boolean;
  /** 이 기기에 서버에 저장되지 않은 노트 초안이 있는지 */
  hasUnsavedDraft: boolean;
  onStart: () => void;
  onResume: () => void;
  onReenter: () => void;
  onCheckState: () => void;
  onRetryMap: () => void;
};

/**
 * 화면 전체를 덮는 흐름 오버레이 (입장 중·입장 실패·수사 준비·복원·연결 교체·채점 대기).
 * 진행 중(playing)·결과 이동(ended)에는 그리지 않는다.
 * 시작·이어하기 버튼은 MAP_READY 뒤에만 누를 수 있다.
 */
export function SessionOverlay(props: SessionOverlayProps) {
  const content = renderContent(props);
  if (content === null) {
    return null;
  }
  return (
    <div className="fixed inset-0 z-40 flex items-center justify-center overflow-y-auto bg-ink/70 p-6">
      <div
        role="dialog"
        aria-modal="true"
        className="flex max-h-full w-full max-w-lg flex-col gap-4 overflow-y-auto rounded-lg bg-surface p-6 shadow-lg"
      >
        {content}
      </div>
    </div>
  );
}

function renderContent({
  phase,
  enterError,
  roomState,
  briefing,
  mapStatus,
  actionError,
  isSubmitting,
  hasUnsavedDraft,
  onStart,
  onResume,
  onReenter,
  onCheckState,
  onRetryMap,
}: SessionOverlayProps): ReactNode {
  switch (phase) {
    case 'playing':
    case 'ended':
      return null;
    case 'entering':
      return <LoadingIndicator label="수사 정보를 불러오는 중…" />;
    case 'enterFailed':
      return (
        <>
          <h2 className="text-lg font-bold">수사 화면을 열지 못했습니다</h2>
          <ErrorMessage message={enterError ?? ''} onRetry={onReenter} />
          <BackToMainLink />
        </>
      );
    case 'awaitingGrading':
      return (
        <>
          <h2 className="text-lg font-bold">채점 결과를 기다리고 있습니다</h2>
          <p className="text-sm text-muted">
            제한시간이 끝났습니다. 접수된 지목의 채점이 끝나면 결과를 볼 수 있습니다.
          </p>
          {actionError && <ErrorMessage message={actionError} />}
          <div className="flex gap-2">
            <Button onClick={onCheckState}>상태 다시 확인</Button>
            <BackToMainLink />
          </div>
        </>
      );
    case 'replaced':
      return (
        <>
          <h2 className="text-lg font-bold">다른 화면에서 수사를 이어하고 있습니다</h2>
          <p className="text-sm text-muted">이 화면에서 계속하면 다른 화면의 수사는 멈춥니다.</p>
          <div className="flex gap-2">
            <Button onClick={onReenter}>이 화면에서 계속하기</Button>
            <BackToMainLink />
          </div>
        </>
      );
    case 'ready':
    case 'paused': {
      const isReady = phase === 'ready';
      return (
        <>
          <h2 className="text-lg font-bold">
            {isReady ? '수사 준비' : '수사를 이어서 진행합니다'}
          </h2>
          {roomState && <p className="font-bold">{roomState.displayTitle}</p>}
          {isReady
            ? briefing && <BriefingContent briefing={briefing} />
            : roomState && (
                <RestoreSummary roomState={roomState} hasUnsavedDraft={hasUnsavedDraft} />
              )}
          {actionError && <ErrorMessage message={actionError} />}
          {mapStatus === 'failed' ? (
            <ErrorMessage message="맵을 불러오지 못했습니다." onRetry={onRetryMap} />
          ) : (
            <div className="flex items-center gap-3">
              {/* 맵이 준비(MAP_READY)되기 전에는 누를 수 없다 */}
              <Button
                onClick={isReady ? onStart : onResume}
                isLoading={isSubmitting}
                disabled={mapStatus !== 'ready'}
              >
                {isReady ? '수사 시작' : '수사 이어하기'}
              </Button>
              {mapStatus === 'loading' && <LoadingIndicator label="맵을 불러오는 중…" />}
            </div>
          )}
          <BackToMainLink />
        </>
      );
    }
  }
}

/** 복원 화면: 남은 시간·횟수, 처리 중·실패 요청, 미저장 노트 초안 안내 */
function RestoreSummary({
  roomState,
  hasUnsavedDraft,
}: {
  roomState: RoomStateResponse;
  hasUnsavedDraft: boolean;
}) {
  // 처리 중·실패 요청의 자세한 복원(다시 보내기 등)은 3주차 AI 요청 UI에서 한다
  const pendingRequestCount = roomState.aiRequests.length;
  return (
    <div className="flex flex-col gap-2 text-sm">
      <p>
        남은 시간{' '}
        <span className="font-mono">{formatRemainingTime(roomState.timer.remainingMs)}</span>
      </p>
      <p>
        {formatUsage('힌트', roomState.hintCount)} ·{' '}
        {formatUsage('지목', roomState.accusationCount)}
      </p>
      {pendingRequestCount > 0 && (
        <p className="text-muted">처리 중이거나 실패한 요청이 {pendingRequestCount}건 있습니다.</p>
      )}
      {hasUnsavedDraft && (
        <p className="text-danger">이 기기에 저장되지 않은 노트 초안이 있습니다.</p>
      )}
    </div>
  );
}

function BackToMainLink() {
  return (
    <Link to="/" className="self-start text-sm text-accent underline">
      메인으로
    </Link>
  );
}
