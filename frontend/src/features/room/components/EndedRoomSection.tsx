import { Link } from 'react-router';
import { ROOM_STATUS_LABELS } from '../../../shared/constants/labels';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { formatDuration, formatServerDateTime } from '../../../shared/utils/formatTime';
import type { RoomSummaryResponse } from '../types';
import { ROOM_LINK_CLASS_NAME } from './roomLinkStyle';

type EndedRoomSectionProps = {
  rooms: RoomSummaryResponse[] | undefined;
  isLoading: boolean;
  errorMessage: string | null;
  onRetry: () => void;
};

/** "지난 수사" 섹션 (로그인한 경우에만 그린다). 결과 보기는 결과 화면으로 이동한다 */
export function EndedRoomSection({
  rooms,
  isLoading,
  errorMessage,
  onRetry,
}: EndedRoomSectionProps) {
  let content;
  if (errorMessage) {
    content = <ErrorMessage message={errorMessage} onRetry={onRetry} />;
  } else if (isLoading || !rooms) {
    content = <LoadingIndicator label="지난 수사를 불러오는 중…" />;
  } else if (rooms.length === 0) {
    content = <p className="text-muted">아직 완료한 수사가 없습니다.</p>;
  } else {
    content = (
      <ul className="flex flex-col gap-2">
        {rooms.map((room) => (
          <li
            key={room.roomId}
            className="flex flex-wrap items-center gap-x-4 gap-y-2 rounded-lg border border-line bg-surface p-4"
          >
            <span className="min-w-0 flex-1 font-bold">{room.displayTitle}</span>
            <span className="rounded-full border border-line px-2 py-0.5 text-xs">
              {ROOM_STATUS_LABELS[room.status]}
            </span>
            {room.durationSec !== null && (
              <span className="font-mono text-sm">{formatDuration(room.durationSec)} 걸림</span>
            )}
            {room.endedAt && (
              <span className="text-sm text-muted">{formatServerDateTime(room.endedAt)}</span>
            )}
            <Link
              to={`/rooms/${room.roomId}/result`}
              aria-label={`${room.displayTitle} 결과 보기`}
              className={`${ROOM_LINK_CLASS_NAME} border border-line bg-surface text-ink hover:border-accent`}
            >
              결과 보기
            </Link>
          </li>
        ))}
      </ul>
    );
  }

  return (
    <section aria-labelledby="ended-room-title" className="flex flex-col gap-3">
      <h2 id="ended-room-title" className="text-xl font-bold">
        지난 수사
      </h2>
      {content}
    </section>
  );
}
