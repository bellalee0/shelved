import type { RankingResponse } from '../features/ranking/types';
import { listMockRooms, toServerTime } from './roomDb';

// 목 랭킹. 사건 1에는 가상 닉네임의 고정 기록 24건(2페이지 분량, 1·2·2·4 공동 순위 포함)이 있고
// 사건 2에는 없다(빈 랭킹). 여기에 요청자가 목에서 해결(CLEARED)한 세션의 가장 빠른 기록이 더해진다.
// 다른 목 사용자의 세션은 더하지 않는다.

type RankingRecord = Omit<RankingResponse, 'rank'>;

function record(nickname: string, durationSec: number, clearedAt: string): RankingRecord {
  return { nickname, durationSec, clearedAt };
}

const CLOCKTOWER_RECORDS: RankingRecord[] = [
  record('왓슨', 840, '2026-10-03T19:02:45'),
  record('수첩지기', 912, '2026-10-04T20:11:03'),
  record('포와로', 912, '2026-10-05T10:40:12'),
  record('마플', 1001, '2026-10-02T22:15:30'),
  // 5위부터는 페이지 이동을 확인하기 위한 가상 기록이다
  ...Array.from({ length: 20 }, (_, index) =>
    record(
      `탐정${String(index + 1).padStart(2, '0')}`,
      1010 + index * 9,
      `2026-10-06T10:${String(index).padStart(2, '0')}:00`,
    ),
  ),
];

const FIXED_RECORDS: Record<number, RankingRecord[]> = { 1: CLOCKTOWER_RECORDS };

/** 요청자가 목에서 해결한 세션 중 가장 빠른 기록 */
function myBestRecord(userId: number, caseId: number, nickname: string): RankingRecord | null {
  const best = listMockRooms(userId)
    .filter(
      (room) => room.caseId === caseId && room.status === 'CLEARED' && room.endedAtMs !== null,
    )
    .map((room) =>
      record(nickname, Math.floor(room.elapsedActiveMs / 1000), toServerTime(room.endedAtMs!)),
    )
    .sort(compareRecords)[0];
  return best ?? null;
}

function compareRecords(a: RankingRecord, b: RankingRecord) {
  return a.durationSec - b.durationSec || a.clearedAt.localeCompare(b.clearedAt);
}

/** 순위대로 정렬한 전체 랭킹. 같은 초는 공동 순위, 다음 순위는 인원만큼 건너뛴다 */
export function rankingsOf(caseId: number, userId: number, nickname: string): RankingResponse[] {
  const mine = myBestRecord(userId, caseId, nickname);
  const records = [...(FIXED_RECORDS[caseId] ?? []), ...(mine ? [mine] : [])].sort(compareRecords);
  return records.map((item) => ({
    rank: records.findIndex((other) => other.durationSec === item.durationSec) + 1,
    ...item,
  }));
}
