import { Link, Navigate, useParams } from 'react-router';
import { AccusationRecordList } from '../features/accusation';
import { useAuth } from '../features/auth/hooks/useAuth';
import { ResultRankingSection } from '../features/ranking';
import { ResultReveal } from '../features/room/components/ResultReveal';
import { RetryChallengeButton } from '../features/room/components/RetryChallengeButton';
import { useRoomMap } from '../features/room/hooks/useRoomMap';
import { useRoomResult } from '../features/room/hooks/useRoomResult';
import { isApiError } from '../shared/api/apiError';
import { ErrorMessage } from '../shared/components/ErrorMessage';
import { LoadingIndicator } from '../shared/components/LoadingIndicator';
import { parsePositiveInt } from '../shared/utils/parsePositiveInt';

const LINK_BUTTON_CLASS_NAME =
  'inline-flex items-center rounded-md border border-line bg-surface px-4 py-2 text-sm font-medium text-ink hover:border-accent';

/** 결과 화면(/rooms/:roomId/result): 리빌·플레이 기록·내 지목 기록·랭킹·다시 도전 */
export function ResultPage() {
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
  return <ResultScreen key={`${user.userId}-${roomId}`} roomId={roomId} userId={user.userId} />;
}

function ResultScreen({ roomId, userId }: { roomId: number; userId: number }) {
  const resultQuery = useRoomResult(roomId, userId);
  const result = resultQuery.data;
  // 범인 얼굴에만 쓴다. 결과를 받은 뒤에 조회하며, 실패해도 기본 실루엣으로 리빌을 보여 준다
  const mapQuery = useRoomMap(result ? roomId : null, userId);

  const { error } = resultQuery;
  if (isApiError(error) && error.code === 'ROOM_NOT_ENDED') {
    // 아직 끝나지 않은 수사는 게임 화면이 이어하기·채점 대기를 판단한다
    return <Navigate to={`/rooms/${roomId}`} replace state={{ notice: error.message }} />;
  }

  if (!result) {
    return (
      <main className="mx-auto flex max-w-xl flex-col gap-4 px-6 py-12">
        {error ? (
          <ErrorMessage message={error.message} onRetry={() => void resultQuery.refetch()} />
        ) : (
          <LoadingIndicator label="수사 결과를 불러오는 중…" />
        )}
        <Link to="/" className="self-start text-sm text-accent underline">
          메인으로
        </Link>
      </main>
    );
  }

  const culpritSpriteKey =
    mapQuery.data?.suspects.find((suspect) => suspect.suspectId === result.culprit.suspectId)
      ?.spriteKey ?? null;

  return (
    <main className="mx-auto grid max-w-5xl gap-8 px-6 py-10 lg:grid-cols-[minmax(0,3fr)_minmax(0,2fr)]">
      <ResultReveal
        result={result}
        culpritSpriteKey={culpritSpriteKey}
        accusationRecords={<AccusationRecordList accusations={result.accusations} />}
      />
      <div className="flex flex-col gap-6">
        {result.isTest ? (
          <p className="rounded-md border border-line bg-surface px-4 py-3 text-sm">
            관리자 테스트 · 랭킹 미반영
          </p>
        ) : (
          <ResultRankingSection caseId={result.caseId} userId={userId} />
        )}
        <div className="flex flex-wrap gap-2">
          <Link to={`/rooms/${roomId}/records`} className={LINK_BUTTON_CLASS_NAME}>
            수사 기록 보기
          </Link>
          {!result.isTest && <RetryChallengeButton caseId={result.caseId} />}
          <Link to="/" className={LINK_BUTTON_CLASS_NAME}>
            메인으로
          </Link>
        </div>
      </div>
    </main>
  );
}
