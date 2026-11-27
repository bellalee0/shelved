import { Link, useParams, useSearchParams } from 'react-router';
import { useAuth } from '../features/auth/hooks/useAuth';
import { MyRanking, RankingTable, useRankings } from '../features/ranking';
import { isApiError } from '../shared/api/apiError';
import { Button } from '../shared/components/Button';
import { ErrorMessage } from '../shared/components/ErrorMessage';
import { LoadingIndicator } from '../shared/components/LoadingIndicator';
import { parsePositiveInt } from '../shared/utils/parsePositiveInt';

const PAGE_SIZE = 20;
const PAGE_PARAM = 'page';

/** 사건별 랭킹(/cases/:caseId/ranking). 페이지 번호는 API와 같이 0부터이며 주소의 ?page=에 둔다 */
export function RankingPage() {
  const caseId = parsePositiveInt(useParams().caseId);
  const { user } = useAuth();
  const [searchParams, setSearchParams] = useSearchParams();
  // 없거나 올바르지 않은 값은 첫 페이지(0)로 본다
  const page = parsePositiveInt(searchParams.get(PAGE_PARAM)) ?? 0;
  const query = useRankings(caseId, user?.userId ?? null, { page, size: PAGE_SIZE });

  function goToPage(nextPage: number) {
    setSearchParams(nextPage === 0 ? {} : { [PAGE_PARAM]: String(nextPage) });
  }

  const notFound =
    caseId === null || (isApiError(query.error) && query.error.code === 'CASE_NOT_FOUND');

  let content;
  if (notFound) {
    content = <h1 className="text-xl font-bold">사건을 찾을 수 없습니다</h1>;
  } else if (query.isError) {
    content = <ErrorMessage message={query.error.message} onRetry={() => void query.refetch()} />;
  } else if (!query.data) {
    content = <LoadingIndicator label="랭킹을 불러오는 중…" />;
  } else {
    const { displayTitle, myRanking, rankingPage } = query.data;
    content = (
      <>
        <header className="flex flex-col gap-1">
          <h1 className="text-2xl font-bold">{displayTitle}</h1>
          <p className="text-sm text-muted">
            성공한 일반 플레이의 사용자별 최고 기록 · 힌트 횟수 무관 · 일시정지 제외
          </p>
        </header>
        <MyRanking myRanking={myRanking} />
        {rankingPage.totalElements === 0 ? (
          <p className="text-muted">아직 등록된 성공 기록이 없습니다.</p>
        ) : rankingPage.content.length === 0 ? (
          <div className="flex flex-col items-start gap-2">
            <p className="text-muted">이 페이지에는 기록이 없습니다.</p>
            <Button variant="secondary" onClick={() => goToPage(0)}>
              첫 페이지로
            </Button>
          </div>
        ) : (
          <RankingTable
            rankings={rankingPage.content}
            myNickname={myRanking?.nickname ?? null}
            showClearedAt
          />
        )}
        {rankingPage.totalPages > 1 && rankingPage.content.length > 0 && (
          <nav aria-label="랭킹 페이지 이동" className="flex items-center justify-center gap-3">
            <Button
              variant="secondary"
              disabled={page === 0 || query.isPlaceholderData}
              onClick={() => goToPage(page - 1)}
            >
              이전
            </Button>
            <span className="text-sm text-muted">
              {rankingPage.number + 1} / {rankingPage.totalPages}
            </span>
            <Button
              variant="secondary"
              disabled={page >= rankingPage.totalPages - 1 || query.isPlaceholderData}
              onClick={() => goToPage(page + 1)}
            >
              다음
            </Button>
          </nav>
        )}
      </>
    );
  }

  return (
    <main className="mx-auto flex max-w-2xl flex-col gap-5 px-6 py-10">
      {content}
      <Link to="/" className="self-start text-sm text-accent underline">
        메인으로
      </Link>
    </main>
  );
}
