import { Link } from 'react-router';
import { ROOM_STATUS_LABELS } from '../../../shared/constants/labels';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { formatRemainingTime } from '../../../shared/utils/formatTime';
import type { RoomSummaryResponse } from '../types';
import { ROOM_LINK_CLASS_NAME } from './roomLinkStyle';

type ActiveRoomSectionProps = {
  rooms: RoomSummaryResponse[] | undefined;
  isLoading: boolean;
  errorMessage: string | null;
  onRetry: () => void;
};

/**
 * "진행 중인 수사" 섹션 (로그인한 경우에만 그린다).
 * 이어하기·다시 채점하기는 모두 게임 화면으로 이동한다. 재채점은 게임 화면이 state.pendingAccusation으로 처리한다.
 */
export function ActiveRoomSection({
  rooms,
  isLoading,
  errorMessage,
  onRetry,
}: ActiveRoomSectionProps) {
  let content;
  if (errorMessage) {
    content = <ErrorMessage message={errorMessage} onRetry={onRetry} />;
  } else if (isLoading || !rooms) {
    content = <LoadingIndicator label="진행 중인 수사를 불러오는 중…" />;
  } else if (rooms.length === 0) {
    content = <p className="text-muted">진행 중인 수사가 없습니다. 사건을 골라 시작해 보세요.</p>;
  } else {
    content = (
      <ul className="flex flex-col gap-2">
        {rooms.map((room) => (
          <ActiveRoomRow key={room.roomId} room={room} />
        ))}
      </ul>
    );
  }

  return (
    <section aria-labelledby="active-room-title" className="flex flex-col gap-3">
      <h2 id="active-room-title" className="text-xl font-bold">
        진행 중인 수사
      </h2>
      {content}
    </section>
  );
}

function ActiveRoomRow({ room }: { room: RoomSummaryResponse }) {
  const isGradingFailed = room.pendingGradingStatus === 'ERROR';
  return (
    <li className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-line bg-surface p-4">
      <span className="min-w-0 flex-1 font-bold">{room.displayTitle}</span>
      <span className="text-sm text-muted">{ROOM_STATUS_LABELS[room.status]}</span>
      {room.pendingGradingStatus === 'PENDING' && (
        <span className="rounded-full border border-accent px-2 py-0.5 text-xs text-accent">
          채점 대기
        </span>
      )}
      {isGradingFailed && (
        <span className="rounded-full bg-danger-soft px-2 py-0.5 text-xs text-danger">
          채점 실패
        </span>
      )}
      <span className="font-mono text-sm">남은 {formatRemainingTime(room.remainingMs)}</span>
      <Link
        to={`/rooms/${room.roomId}`}
        aria-label={`${room.displayTitle} ${isGradingFailed ? '다시 채점하기' : '이어하기'}`}
        className={`${ROOM_LINK_CLASS_NAME} ${
          isGradingFailed
            ? 'border border-line bg-surface text-ink hover:border-accent'
            : 'bg-accent text-surface hover:bg-accent-strong'
        }`}
      >
        {isGradingFailed ? '다시 채점하기' : '이어하기'}
      </Link>
    </li>
  );
}
