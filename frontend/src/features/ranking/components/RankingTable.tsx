import { formatDuration, formatServerDateTime } from '../../../shared/utils/formatTime';
import type { RankingResponse } from '../types';

type RankingTableProps = {
  /** 서버가 정한 순서 그대로 그린다 (같은 순위 안의 순서도 서버가 정한다) */
  rankings: RankingResponse[];
  /** 내 줄을 강조한다 */
  myNickname: string | null;
  /** 달성 시각 열을 보여 줄지 (좁은 결과 화면에서는 뺀다) */
  showClearedAt?: boolean;
};

export function RankingTable({ rankings, myNickname, showClearedAt = false }: RankingTableProps) {
  return (
    <table className="w-full border-collapse rounded-md border border-line bg-surface text-sm">
      <thead>
        <tr className="border-b border-line text-left text-muted">
          <th scope="col" className="px-3 py-2 font-medium">
            순위
          </th>
          <th scope="col" className="px-3 py-2 font-medium">
            닉네임
          </th>
          <th scope="col" className="px-3 py-2 text-right font-medium">
            걸린 시간
          </th>
          {showClearedAt && (
            <th scope="col" className="px-3 py-2 text-right font-medium">
              달성 시각
            </th>
          )}
        </tr>
      </thead>
      <tbody>
        {rankings.map((ranking) => {
          const isMine = ranking.nickname === myNickname;
          return (
            // 닉네임은 중복되지 않고 사용자당 기록이 하나라 줄을 구분할 수 있다
            <tr
              key={ranking.nickname}
              aria-current={isMine || undefined}
              className={`border-b border-line last:border-b-0 ${isMine ? 'bg-paper font-bold' : ''}`}
            >
              <td className="px-3 py-2 font-mono">{ranking.rank}</td>
              <td className="px-3 py-2 break-all">{ranking.nickname}</td>
              <td className="px-3 py-2 text-right font-mono">
                {formatDuration(ranking.durationSec)}
              </td>
              {showClearedAt && (
                <td className="px-3 py-2 text-right text-muted">
                  {formatServerDateTime(ranking.clearedAt)}
                </td>
              )}
            </tr>
          );
        })}
      </tbody>
    </table>
  );
}
