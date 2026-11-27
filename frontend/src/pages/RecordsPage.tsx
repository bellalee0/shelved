import { Link, useLocation, useNavigate, useParams } from 'react-router';
import { useAuth } from '../features/auth/hooks/useAuth';
import { HintRecords } from '../features/hint';
import { NoteRecords } from '../features/note';
import { joinConversations, NpcRecord } from '../features/npc';
import { useRoomMap } from '../features/room/hooks/useRoomMap';
import { useRoomState } from '../features/room/hooks/useRoomState';
import { isEndedStatus } from '../features/room/sessionPhase';
import { Button } from '../shared/components/Button';
import { ErrorMessage } from '../shared/components/ErrorMessage';
import { LoadingIndicator } from '../shared/components/LoadingIndicator';
import { parsePositiveInt } from '../shared/utils/parsePositiveInt';

/**
 * 수사 기록 보기(/rooms/:roomId/records), 읽기 전용.
 * 게임 화면과 별개라 맵·하트비트·재개가 없다. 보내는 요청은 GET state·map·NPC messages·hints·notes/me뿐이다.
 */
export function RecordsPage() {
  const roomId = parsePositiveInt(useParams().roomId);
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
  return <RecordsScreen key={`${user.userId}-${roomId}`} roomId={roomId} userId={user.userId} />;
}

function RecordsScreen({ roomId, userId }: { roomId: number; userId: number }) {
  const navigate = useNavigate();
  const location = useLocation();
  const stateQuery = useRoomState(roomId, userId);
  const roomState = stateQuery.data;

  function handleBack() {
    // 'default'는 이 앱에서 이동해 온 기록이 없다는 뜻이다 (주소로 직접 들어온 경우)
    if (location.key === 'default') {
      navigate(`/rooms/${roomId}`);
    } else {
      navigate(-1);
    }
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-8 px-6 py-10">
      <header className="flex flex-col gap-1">
        <h1 className="text-2xl font-bold">수사 기록 · 읽기 전용</h1>
        {roomState && <p className="text-muted">{roomState.displayTitle}</p>}
      </header>

      {stateQuery.isError ? (
        <ErrorMessage
          message={stateQuery.error.message}
          onRetry={() => void stateQuery.refetch()}
        />
      ) : !roomState ? (
        <LoadingIndicator label="수사 기록을 불러오는 중…" />
      ) : (
        <>
          <section aria-labelledby="record-npc-title" className="flex flex-col gap-3">
            <h2 id="record-npc-title" className="text-lg font-bold">
              NPC 대화 원문
            </h2>
            <ConversationRecords
              roomId={roomId}
              userId={userId}
              conversations={roomState.conversations}
            />
          </section>
          <section aria-labelledby="record-hint-title" className="flex flex-col gap-3">
            <h2 id="record-hint-title" className="text-lg font-bold">
              힌트·보충 질문 기록
            </h2>
            <HintRecords roomId={roomId} />
          </section>
          <NoteRecords
            roomId={roomId}
            userId={userId}
            canSaveLater={!isEndedStatus(roomState.timer.status) && roomState.timer.remainingMs > 0}
          />
        </>
      )}

      <div className="flex gap-2">
        <Button variant="secondary" onClick={handleBack}>
          이전 화면으로
        </Button>
        <Link to="/" className="self-center text-sm text-accent underline">
          메인으로
        </Link>
      </div>
    </main>
  );
}

type ConversationRecordsProps = {
  roomId: number;
  userId: number;
  conversations: { suspectId: number; messageCount: number; lastMessageAt: string }[];
};

/** 대화한 NPC마다 원문을 보여 준다. 이름은 GET map의 suspects에서 가져온다 */
function ConversationRecords({ roomId, userId, conversations }: ConversationRecordsProps) {
  const mapQuery = useRoomMap(roomId, userId);

  if (conversations.length === 0) {
    return <p className="text-sm text-muted">아직 기록이 없습니다.</p>;
  }
  // 이름을 못 받아도 원문은 볼 수 있게, 조회가 끝나기(실패 포함) 전까지만 기다린다
  if (mapQuery.isPending) {
    return <LoadingIndicator label="인물 정보를 불러오는 중…" />;
  }
  return (
    <>
      {joinConversations(conversations, mapQuery.data?.suspects ?? []).map((item) => (
        <NpcRecord
          key={item.suspectId}
          roomId={roomId}
          suspectId={item.suspectId}
          name={item.name}
        />
      ))}
    </>
  );
}
