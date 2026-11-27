import { beforeEach, describe, expect, it } from 'vitest';
import { getRankings } from '../../features/ranking/api';
import { getResult } from '../../features/room/api';
import { seedMockRoom } from '../roomDb';
import { loginMockUser, resetLoginState } from '../testing/loginMockUser';

beforeEach(() => {
  resetLoginState();
});

describe('결과 목', () => {
  it.each(['READY', 'IN_PROGRESS', 'PAUSED'] as const)(
    '%s 세션의 결과는 409 ROOM_NOT_ENDED다',
    async (status) => {
      // given
      const user = await loginMockUser();
      const room = seedMockRoom(user.userId, { caseId: 1, status });

      // when
      const request = getResult(room.roomId);

      // then
      await expect(request).rejects.toMatchObject({ status: 409, code: 'ROOM_NOT_ENDED' });
    },
  );
});

describe('랭킹 목', () => {
  it('페이지를 나눠 주고 공동 순위 다음은 건너뛴다', async () => {
    // given
    await loginMockUser();

    // when
    const first = await getRankings(1, { page: 0, size: 3 });
    const second = await getRankings(1, { page: 1, size: 3 });

    // then
    expect(first.rankingPage).toMatchObject({ totalElements: 24, totalPages: 8, number: 0 });
    expect(first.rankingPage.content.map((item) => item.rank)).toEqual([1, 2, 2]);
    expect(second.rankingPage.content[0].rank).toBe(4);
    expect(first.myRanking).toBeNull();
  });

  it('없는 사건은 404 CASE_NOT_FOUND다', async () => {
    // given
    await loginMockUser();

    // when
    const request = getRankings(999, { page: 0, size: 20 });

    // then
    await expect(request).rejects.toMatchObject({ status: 404, code: 'CASE_NOT_FOUND' });
  });
});
