import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { server } from '../mocks/node';
import { seedMockRoom } from '../mocks/roomDb';
import { loginMockUser, resetLoginState } from '../mocks/testing/loginMockUser';
import { recordApiRequests } from '../mocks/testing/recordRequests';
import { renderRoute } from '../mocks/testing/renderRoute';

/** 순위표의 본문 줄을 칸 글자 목록으로 읽는다 */
function bodyRows() {
  return within(screen.getByRole('table'))
    .getAllByRole('row')
    .slice(1)
    .map((row) =>
      within(row)
        .getAllByRole('cell')
        .map((cell) => cell.textContent),
    );
}

beforeEach(() => {
  resetLoginState();
});

afterEach(() => {
  server.events.removeAllListeners();
});

describe('랭킹 화면', () => {
  it('공동 순위를 같은 숫자로, 서버 순서대로 보여 주고 성공 기록이 없으면 안내한다', async () => {
    // given
    await loginMockUser();

    // when
    renderRoute('/cases/1/ranking');
    await screen.findByRole('table');

    // then
    expect(
      screen.getByRole('heading', { name: '시계탑 저택의 마지막 종소리' }),
    ).toBeInTheDocument();
    expect(bodyRows().slice(0, 4)).toEqual([
      ['1', '왓슨', '14:00', '10/03 19:02'],
      ['2', '수첩지기', '15:12', '10/04 20:11'],
      ['2', '포와로', '15:12', '10/05 10:40'],
      ['4', '마플', '16:41', '10/02 22:15'],
    ]);
    expect(bodyRows()).toHaveLength(20);
    expect(screen.getByText(/아직 성공 기록이 없습니다/)).toBeInTheDocument();
  });

  it('페이지 이동은 page 쿼리로 요청하고, 내 기록은 현재 페이지에 없어도 보여 준다', async () => {
    // given 내 기록(19:59)은 2페이지에 있다
    const user = await loginMockUser();
    seedMockRoom(user.userId, { caseId: 1, status: 'CLEARED', durationSec: 1199 });
    const requests = recordApiRequests();
    const router = renderRoute('/cases/1/ranking');
    await screen.findByRole('table');
    expect(screen.getByRole('group', { name: '내 최고 기록' })).toHaveTextContent('25위');
    expect(screen.queryByRole('cell', { name: '셜록초보' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '이전' })).toBeDisabled();

    // when
    fireEvent.click(screen.getByRole('button', { name: '다음' }));

    // then
    expect(await screen.findByRole('cell', { name: '셜록초보' })).toBeInTheDocument();
    expect(router.state.location.search).toBe('?page=1');
    expect(requests).toContain('GET /api/rankings/1?page=1&size=20');
    expect(screen.getByText('2 / 2')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '다음' })).toBeDisabled();

    // when
    fireEvent.click(screen.getByRole('button', { name: '이전' }));

    // then
    await waitFor(() => expect(router.state.location.search).toBe(''));
  });

  it('주소의 ?page=로 바로 들어오면 그 페이지를 요청한다', async () => {
    // given
    await loginMockUser();
    const requests = recordApiRequests();

    // when
    renderRoute('/cases/1/ranking?page=1');

    // then
    await screen.findByRole('table');
    expect(requests).toContain('GET /api/rankings/1?page=1&size=20');
    expect(bodyRows()).toHaveLength(4);
  });

  it('범위를 벗어난 페이지는 첫 페이지로 돌아갈 수 있다', async () => {
    // given
    await loginMockUser();
    const router = renderRoute('/cases/1/ranking?page=9');

    // when
    fireEvent.click(await screen.findByRole('button', { name: '첫 페이지로' }));

    // then
    expect(await screen.findByRole('table')).toBeInTheDocument();
    expect(router.state.location.search).toBe('');
  });

  it('기록이 없으면 빈 상태를 보여 준다', async () => {
    // given
    await loginMockUser();

    // when
    renderRoute('/cases/2/ranking');

    // then
    expect(await screen.findByText('아직 등록된 성공 기록이 없습니다.')).toBeInTheDocument();
    expect(screen.queryByRole('table')).not.toBeInTheDocument();
  });

  it.each(['/cases/999/ranking', '/cases/abc/ranking'])(
    '%s 는 사건을 찾을 수 없다고 안내하고 메인으로 갈 수 있다',
    async (path) => {
      // given
      await loginMockUser();

      // when
      renderRoute(path);

      // then
      expect(
        await screen.findByRole('heading', { name: '사건을 찾을 수 없습니다' }),
      ).toBeInTheDocument();
      expect(screen.getByRole('link', { name: '메인으로' })).toHaveAttribute('href', '/');
    },
  );

  it('비로그인이면 로그인 화면으로 가고 돌아올 경로를 넘긴다', async () => {
    // given
    const requests = recordApiRequests();

    // when
    const router = renderRoute('/cases/1/ranking?page=1');

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.state).toEqual({ from: '/cases/1/ranking?page=1' });
    expect(requests.some((request) => request.includes('/api/rankings'))).toBe(false);
  });

  it('메인 사건 카드의 "랭킹"은 비로그인에게도 보이고 누르면 로그인 화면으로 간다', async () => {
    // given
    const router = renderRoute('/');

    // when
    fireEvent.click(await screen.findByRole('link', { name: '시계탑 저택의 마지막 종소리 랭킹' }));

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'));
    expect(router.state.location.state).toEqual({ from: '/cases/1/ranking' });
  });

  it('로그인한 사용자는 사건 카드의 "랭킹"으로 랭킹 화면에 간다', async () => {
    // given
    await loginMockUser();
    const router = renderRoute('/');

    // when
    fireEvent.click(await screen.findByRole('link', { name: '안개 낀 등대의 빈 의자 랭킹' }));

    // then
    expect(await screen.findByText('아직 등록된 성공 기록이 없습니다.')).toBeInTheDocument();
    expect(router.state.location.pathname).toBe('/cases/2/ranking');
  });
});
