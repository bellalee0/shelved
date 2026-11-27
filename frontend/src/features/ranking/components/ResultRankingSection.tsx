import { Link } from 'react-router';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { useRankings } from '../hooks/useRankings';
import { MyRanking } from './MyRanking';
import { RankingTable } from './RankingTable';

/** 결과 화면에 보여 줄 상위 기록 수 */
const RESULT_RANKING_SIZE = 10;

/**
 * 결과 화면의 랭킹 영역: 내 최고 기록, 상위 목록, 전체 랭킹 보기.
 * 결과(리빌)와 따로 조회하므로 여기서 실패해도 리빌은 그대로 보인다.
 */
export function ResultRankingSection({ caseId, userId }: { caseId: number; userId: number }) {
  const query = useRankings(caseId, userId, { page: 0, size: RESULT_RANKING_SIZE });

  let content;
  if (query.isError) {
    content = <ErrorMessage message={query.error.message} onRetry={() => void query.refetch()} />;
  } else if (!query.data) {
    content = <LoadingIndicator label="랭킹을 불러오는 중…" />;
  } else {
    const { myRanking, rankingPage } = query.data;
    content = (
      <>
        <MyRanking myRanking={myRanking} />
        {rankingPage.content.length === 0 ? (
          <p className="text-sm text-muted">아직 등록된 성공 기록이 없습니다.</p>
        ) : (
          <RankingTable rankings={rankingPage.content} myNickname={myRanking?.nickname ?? null} />
        )}
        <Link to={`/cases/${caseId}/ranking`} className="self-start text-sm text-accent underline">
          전체 랭킹 보기
        </Link>
      </>
    );
  }

  return (
    <section aria-labelledby="result-ranking-title" className="flex flex-col gap-3">
      <h2 id="result-ranking-title" className="text-lg font-bold">
        랭킹
      </h2>
      <p className="text-xs text-muted">사용자별 최고 기록 · 힌트 사용량은 반영하지 않음</p>
      {content}
    </section>
  );
}
