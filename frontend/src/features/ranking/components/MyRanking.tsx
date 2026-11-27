import { formatDuration, formatServerDateTime } from '../../../shared/utils/formatTime';
import type { RankingResponse } from '../types';

/** 내 최고 기록. 지금 보는 페이지에 없어도 전체 기준 순위를 보여 준다 */
export function MyRanking({ myRanking }: { myRanking: RankingResponse | null }) {
  return (
    <div
      role="group"
      aria-label="내 최고 기록"
      className="flex flex-wrap items-center gap-x-4 gap-y-1 rounded-md border border-accent bg-paper px-4 py-3 text-sm"
    >
      {myRanking ? (
        <>
          <span className="text-muted">내 최고 기록</span>
          <span className="font-bold">{myRanking.rank}위</span>
          <span className="font-mono">{formatDuration(myRanking.durationSec)}</span>
          <span className="text-muted">{formatServerDateTime(myRanking.clearedAt)}</span>
        </>
      ) : (
        <p className="text-muted">
          아직 성공 기록이 없습니다. 실패·시간 만료 기록은 랭킹에 들어가지 않습니다.
        </p>
      )}
    </div>
  );
}
