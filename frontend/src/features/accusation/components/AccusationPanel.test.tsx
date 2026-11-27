import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useCallback, useEffect, useState } from 'react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { acceptAccusation } from '../../../mocks/accusationDb';
import { server } from '../../../mocks/node';
import { currentTimeMs, findMockRoom, saveMockRoomDb, setMockClock } from '../../../mocks/roomDb';
import { setScenario } from '../../../mocks/scenarios';
import { loginMockUser, resetLoginState } from '../../../mocks/testing/loginMockUser';
import { resetApiSession, setConnectionId } from '../../../shared/api/client';
import type { AiRequestStatusResponse } from '../../../shared/api/types';
import { createRoom, getMap, getRoomState, startRoom } from '../../room/api';
import type { RoomMapResponse, RoomStateResponse } from '../../room/types';
import { createAccusation } from '../api';
import { useAccusation } from '../hooks/useAccusation';
import { AccusationPanel } from './AccusationPanel';

// 지목 창 + 게임 화면이 가진 지목 상태(useAccusation)를 함께 그려, 실제 MSW 목과 주고받는 흐름을 확인한다.
// 목의 판정 규칙(가상 사건): 사건 1의 범인은 마거릿(suspectId 2), "종소리"·"시계" 둘 다 → 정답, 하나 → 부분 정답.

const CORRECT = '종소리가 울리기 전에 시계를 미리 돌려 두었다.';
const PARTIAL = '시계를 미리 돌려 두어 시각을 속였다.';
const ID = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;
const NO_REQUESTS: AiRequestStatusResponse[] = [];

let roomId = 0;
let userId = 0;
let mapData: RoomMapResponse;

/** POST accusations 요청의 연결 헤더·본문을 순서대로 모은다 */
const posts: { connectionId: string | null; body: Record<string, unknown> }[] = [];
const requestedPaths: string[] = [];
async function recordRequest({ request }: { request: Request }) {
  const url = new URL(request.url);
  requestedPaths.push(`${request.method} ${url.pathname}`);
  if (request.method === 'POST' && url.pathname.endsWith('/accusations')) {
    posts.push({
      connectionId: request.headers.get('X-Connection-Id'),
      body: (await request.clone().json()) as Record<string, unknown>,
    });
  }
}

type HarnessProps = {
  sessionEnded?: boolean;
  serverClockOffsetMs?: number;
  reconnectKey?: number;
  onRetryLater?: () => void;
  onShowResult?: () => void;
  onFinalResult?: () => void;
  onStateChanged?: () => void;
};

/** 게임 화면처럼 GET state를 들고 지목 상태를 화면 단위로 가진다. "지목 창 열기"로 창만 다시 연다 */
function Harness({
  sessionEnded = false,
  serverClockOffsetMs = 0,
  reconnectKey = 0,
  onRetryLater = () => undefined,
  onShowResult = () => undefined,
  onFinalResult,
  onStateChanged,
}: HarnessProps) {
  const [roomState, setRoomState] = useState<RoomStateResponse | null>(null);
  const [open, setOpen] = useState(true);
  const refreshState = useCallback(() => {
    onStateChanged?.();
    void getRoomState(roomId).then(setRoomState);
  }, [onStateChanged]);
  useEffect(() => {
    void getRoomState(roomId).then(setRoomState);
  }, []);
  const accusation = useAccusation({
    roomId,
    aiRequests: roomState?.aiRequests ?? NO_REQUESTS,
    pendingAccusation: roomState?.pendingAccusation ?? null,
    reconnectKey,
    onStateChanged: refreshState,
    onSessionRejected: refreshState,
    onFinalResult,
  });
  if (!open) {
    return (
      <button type="button" onClick={() => setOpen(true)}>
        지목 창 열기
      </button>
    );
  }
  return (
    <AccusationPanel
      roomId={roomId}
      accusation={accusation}
      suspects={mapData.suspects}
      renderFace={(suspect) => (
        <span data-testid={suspect.spriteKey ? 'face' : 'face-fallback'}>{suspect.name[0]}</span>
      )}
      timerStatus={roomState?.timer.status ?? null}
      remainingMs={roomState?.timer.remainingMs ?? null}
      awaitingGrading={roomState?.timer.awaitingGrading ?? false}
      hasConnection
      accusationCount={roomState?.accusationCount ?? null}
      pendingAccusation={roomState?.pendingAccusation ?? null}
      sessionEnded={sessionEnded}
      serverClockOffsetMs={serverClockOffsetMs}
      resyncKey={reconnectKey}
      onShowResult={onShowResult}
      onRetryLater={onRetryLater}
      onClose={() => setOpen(false)}
    />
  );
}

function renderHarness(props: HarnessProps = {}) {
  const queryClient = new QueryClient();
  const view = render(
    <QueryClientProvider client={queryClient}>
      <Harness {...props} />
    </QueryClientProvider>,
  );
  return {
    ...view,
    rerenderWith(next: HarnessProps) {
      view.rerender(
        <QueryClientProvider client={queryClient}>
          <Harness {...props} {...next} />
        </QueryClientProvider>,
      );
    },
  };
}

async function fillAnswer(suspectName: string, trick: string) {
  fireEvent.click(await screen.findByRole('radio', { name: new RegExp(suspectName) }));
  fireEvent.change(screen.getByLabelText('트릭 설명'), { target: { value: trick } });
}

/** "제출" → 확인 → "제출하기" */
async function submitAnswer(suspectName: string, trick: string) {
  await fillAnswer(suspectName, trick);
  const submit = screen.getByRole('button', { name: '제출' });
  await waitFor(() => expect(submit).toBeEnabled());
  fireEvent.click(submit);
  fireEvent.click(screen.getByRole('button', { name: '제출하기' }));
}

function records() {
  return screen.getByRole('list', { name: '지목 기록' });
}

beforeEach(async () => {
  resetLoginState();
  userId = (await loginMockUser()).userId;
  roomId = (await createRoom({ caseId: 1 })).roomId;
  setConnectionId((await startRoom(roomId)).connectionId);
  mapData = await getMap(roomId);
  posts.length = 0;
  requestedPaths.length = 0;
  server.events.on('request:start', recordRequest);
});

afterEach(() => {
  server.events.removeListener('request:start', recordRequest);
  resetApiSession();
  setMockClock(null);
});

describe('AccusationPanel 입력', () => {
  it('맵에 배치되지 않은 인물까지 대상으로 보여 주고, 스프라이트가 없으면 기본 얼굴을 쓴다', async () => {
    // when
    renderHarness();

    // then
    const radios = await screen.findAllByRole('radio');
    expect(radios).toHaveLength(4);
    expect(screen.getByRole('radio', { name: /엘리스/ })).toBeInTheDocument();
    expect(screen.getAllByTestId('face')).toHaveLength(3);
    expect(screen.getAllByTestId('face-fallback')).toHaveLength(1);
  });

  it('글자 수를 보여 주고 10자 미만·3000자 초과면 제출을 막고 이유를 알려 준다', async () => {
    // given
    renderHarness();
    await screen.findByText(/남은 지목 3/);

    // when 인물만 고른 상태
    fireEvent.click(await screen.findByRole('radio', { name: /마거릿/ }));

    // then
    expect(screen.getByRole('button', { name: '제출' })).toBeDisabled();
    expect(screen.getByText('트릭 설명을 10자 이상 입력해 주세요.')).toBeInTheDocument();

    // when
    fireEvent.change(screen.getByLabelText('트릭 설명'), { target: { value: '가'.repeat(3001) } });

    // then
    expect(screen.getByText('3001 / 3000자')).toBeInTheDocument();
    expect(screen.getByText('트릭 설명은 3000자까지 입력할 수 있습니다.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '제출' })).toBeDisabled();

    // when
    fireEvent.change(screen.getByLabelText('트릭 설명'), { target: { value: PARTIAL } });

    // then
    expect(screen.getByRole('button', { name: '제출' })).toBeEnabled();
  });

  it('제출 전 기회 사용 안내를 항상 보여 주고, Enter로는 제출되지 않는다', async () => {
    // given
    renderHarness();
    await fillAnswer('마거릿', PARTIAL);
    expect(
      await screen.findByText(
        '제출하면 지목 기회 1회를 사용합니다 (남은 3회). 채점에 실패하면 차감되지 않습니다.',
      ),
    ).toBeInTheDocument();

    // when
    fireEvent.keyDown(screen.getByLabelText('트릭 설명'), { key: 'Enter' });

    // then
    expect(screen.queryByRole('group', { name: '제출 확인' })).not.toBeInTheDocument();
    expect(posts).toHaveLength(0);
  });

  it('제출을 누르면 대상 이름과 함께 확인을 거치고, 취소하면 보내지 않는다', async () => {
    // given
    renderHarness();
    await fillAnswer('마거릿', PARTIAL);
    await waitFor(() => expect(screen.getByRole('button', { name: '제출' })).toBeEnabled());

    // when
    fireEvent.click(screen.getByRole('button', { name: '제출' }));

    // then
    const confirm = screen.getByRole('group', { name: '제출 확인' });
    expect(confirm).toHaveTextContent('마거릿을(를) 범인으로 지목합니다. 이대로 제출할까요?');

    // when
    fireEvent.click(within(confirm).getByRole('button', { name: '취소' }));

    // then
    expect(posts).toHaveLength(0);
    expect(screen.getByRole('button', { name: '제출' })).toBeEnabled();
  });
});

describe('AccusationPanel 채점 중', () => {
  it('채점 중에는 입력·대상·제출을 잠그고, 확인 버튼을 연속으로 눌러도 한 번만 보낸다', async () => {
    // given
    setScenario('accusationSlow', true);
    renderHarness();
    await fillAnswer('마거릿', PARTIAL);
    await waitFor(() => expect(screen.getByRole('button', { name: '제출' })).toBeEnabled());
    fireEvent.click(screen.getByRole('button', { name: '제출' }));
    const confirmButton = screen.getByRole('button', { name: '제출하기' });

    // when
    fireEvent.click(confirmButton);
    fireEvent.click(confirmButton);

    // then
    expect((await screen.findAllByText('채점 중입니다…')).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('트릭 설명')).toBeDisabled();
    expect(screen.getByRole('radio', { name: /마거릿/ })).toBeDisabled();
    expect(screen.getByRole('button', { name: '제출' })).toBeDisabled();
    expect(posts).toHaveLength(1);
    expect(screen.getByLabelText('지목 횟수')).toHaveTextContent('남은 지목 2 (처리 중 1)');
  });

  it('창을 닫았다 열어도 채점 요청과 입력 초안이 그대로 이어진다', async () => {
    // given
    setScenario('accusationSlow', true);
    renderHarness();
    await submitAnswer('마거릿', PARTIAL);
    await screen.findAllByText('채점 중입니다…');

    // when
    fireEvent.click(screen.getByRole('button', { name: '패널 닫기' }));
    fireEvent.click(screen.getByRole('button', { name: '지목 창 열기' }));

    // then 다시 열어도 채점 중이고, 상태 조회가 이어져 결과가 나온다
    expect((await screen.findAllByText('채점 중입니다…')).length).toBeGreaterThan(0);
    expect(screen.getByLabelText('트릭 설명')).toHaveValue(PARTIAL);
    expect(screen.getByRole('radio', { name: /마거릿/ })).toBeChecked();
    expect(await screen.findByText('방금 받은 결과', {}, { timeout: 6000 })).toBeInTheDocument();
    expect(posts).toHaveLength(1);
  }, 10_000);
});

describe('AccusationPanel 결과', () => {
  it('부분 정답이면 피드백(줄바꿈 유지)과 남은 기회를 보여 주고, 기록·상태를 다시 받으며 답안을 남겨 둔다', async () => {
    // given
    const onStateChanged = vi.fn();
    renderHarness({ onStateChanged });

    // when
    await submitAnswer('마거릿', PARTIAL);

    // then
    const highlighted = await screen.findByText('방금 받은 결과');
    const item = highlighted.closest('li')!;
    expect(item).toHaveTextContent('1번째 지목');
    expect(item).toHaveTextContent('부분 정답');
    const feedback = within(item).getByText(/범인을 바르게 짚었습니다/);
    expect(feedback.textContent).toContain('\n\n');
    expect(feedback).toHaveClass('whitespace-pre-wrap');
    await waitFor(() => expect(screen.getByRole('status')).toHaveTextContent('남은 기회 2회'));
    expect(onStateChanged).toHaveBeenCalled();
    // 창을 열 때 한 번 + 결과를 받은 뒤 다시 받는다
    await waitFor(() =>
      expect(
        requestedPaths.filter((path) => path === `GET /api/rooms/${roomId}/accusations`).length,
      ).toBeGreaterThanOrEqual(2),
    );
    expect(screen.getByLabelText('트릭 설명')).toHaveValue(PARTIAL);
    expect(screen.getByRole('button', { name: '제출' })).toBeEnabled();
  });

  it('정답이면 종료를 확인하는 동안 기다리고, 종료가 확인되면 "결과 보기"를 보여 준다 (자동 이동 없음)', async () => {
    // given
    const onFinalResult = vi.fn();
    const onShowResult = vi.fn();
    const view = renderHarness({ onFinalResult, onShowResult });

    // when
    await submitAnswer('마거릿', CORRECT);

    // then 종료 확인 전
    expect(await screen.findByText('수사 결과를 확인하는 중…')).toBeInTheDocument();
    expect(onFinalResult).toHaveBeenCalledTimes(1);
    expect(screen.queryByRole('button', { name: '결과 보기' })).not.toBeInTheDocument();

    // when 게임 화면이 GET state로 종료를 확인했다
    view.rerenderWith({ onFinalResult, onShowResult, sessionEnded: true });

    // then 화면 가운데 팝업에 종료 안내·방금 받은 판정과 피드백·결과 보기가 함께 보인다
    const popup = await screen.findByRole('dialog', { name: '수사 종료' });
    expect(popup).toHaveTextContent('정답입니다! 사건을 해결했습니다.');
    await waitFor(() => expect(popup).toHaveTextContent(/판정\s*정답/));
    expect(screen.queryByLabelText('트릭 설명')).not.toBeInTheDocument();
    fireEvent.click(within(popup).getByRole('button', { name: '결과 보기' }));
    expect(onShowResult).toHaveBeenCalledTimes(1);
  });

  it('종료 팝업을 닫으면 지목 창은 남고, 창 안의 "결과 보기"를 계속 쓸 수 있다', async () => {
    // given
    const onShowResult = vi.fn();
    const view = renderHarness({ onShowResult });
    await submitAnswer('마거릿', CORRECT);
    await screen.findByText('수사 결과를 확인하는 중…');
    view.rerenderWith({ onShowResult, sessionEnded: true });
    const popup = await screen.findByRole('dialog', { name: '수사 종료' });

    // when
    fireEvent.click(within(popup).getByRole('button', { name: '닫기' }));

    // then
    expect(screen.queryByRole('dialog', { name: '수사 종료' })).not.toBeInTheDocument();
    expect(screen.getByRole('button', { name: '패널 닫기' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: '결과 보기' }));
    expect(onShowResult).toHaveBeenCalledTimes(1);
  });

  it('긴 트릭 설명은 접어서 보여 주고 펼칠 수 있다', async () => {
    // given
    const long = `${PARTIAL} ${'추가 설명입니다. '.repeat(20)}끝`;
    await createAccusation(
      roomId,
      { requestId: ID(1), suspectId: 2, trickExplanation: long },
      { isRetry: false },
    );

    // when
    renderHarness();

    // then
    const item = await within(await screen.findByRole('list', { name: '지목 기록' })).findByRole(
      'listitem',
    );
    expect(item).not.toHaveTextContent('끝');
    fireEvent.click(within(item).getByRole('button', { name: '펼치기' }));
    expect(item).toHaveTextContent('끝');
  });
});

describe('AccusationPanel 채점 실패와 재채점', () => {
  it('실패하면 차감되지 않았다고 안내하고, 최소 간격 전에는 재채점 버튼을 막으며 자동으로 다시 보내지 않는다', async () => {
    // given
    setScenario('accusationFail', true);
    renderHarness();

    // when
    await submitAnswer('마거릿', PARTIAL);

    // then
    const alert = await screen.findByText('채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.');
    const retryArea = alert.closest('[role="alert"]') as HTMLElement;
    expect(within(retryArea).getByRole('button', { name: '같은 답안 다시 채점' })).toBeDisabled();
    expect(within(retryArea).getByText(/\d+초 후 다시 채점할 수 있습니다/)).toBeInTheDocument();
    await act(() => new Promise((resolve) => setTimeout(resolve, 300)));
    expect(posts).toHaveLength(1);
    await waitFor(() =>
      expect(screen.getByLabelText('지목 횟수')).toHaveTextContent('남은 지목 3'),
    );
  });

  it('재채점은 같은 requestId·같은 답안을 연결 헤더 없이 보내고, 기회를 1회만 쓴다', async () => {
    // given
    setScenario('accusationFail', true);
    const view = renderHarness();
    await submitAnswer('마거릿', PARTIAL);
    await screen.findByText('채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.');

    // when 10초가 지났다 (목 서버 시각과 화면의 서버 시각 차이를 함께 옮긴다)
    const base = currentTimeMs();
    setMockClock(() => base + 11_000);
    view.rerenderWith({ serverClockOffsetMs: 11_000 });
    const retry = screen.getByRole('button', { name: '같은 답안 다시 채점' });
    await waitFor(() => expect(retry).toBeEnabled());
    fireEvent.click(retry);

    // then
    expect(await screen.findByText('방금 받은 결과')).toBeInTheDocument();
    expect(posts).toHaveLength(2);
    expect(posts[0].connectionId).not.toBeNull();
    expect(posts[1].connectionId).toBeNull();
    expect(posts[1].body).toEqual(posts[0].body);
    await waitFor(() =>
      expect(screen.getByLabelText('지목 횟수')).toHaveTextContent('남은 지목 2'),
    );
  });

  it('간격 전에 보내 429를 받으면 실패 상태와 재채점 영역을 유지한다', async () => {
    // given 화면만 시간이 지난 것으로 보고, 목 서버는 아직 10초가 지나지 않았다
    setScenario('accusationFail', true);
    const view = renderHarness();
    await submitAnswer('마거릿', PARTIAL);
    await screen.findByText('채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.');
    view.rerenderWith({ serverClockOffsetMs: 11_000 });

    // when
    fireEvent.click(await screen.findByRole('button', { name: '같은 답안 다시 채점' }));

    // then
    await waitFor(() => expect(posts).toHaveLength(2));
    expect(
      await screen.findByText('채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.'),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '같은 답안 다시 채점' })).toBeInTheDocument();
  });

  it('"나중에 다시 시도"는 게임 화면의 나가기 흐름을 부른다', async () => {
    // given
    setScenario('accusationFail', true);
    const onRetryLater = vi.fn();
    renderHarness({ onRetryLater });
    await submitAnswer('마거릿', PARTIAL);

    // when
    fireEvent.click(await screen.findByRole('button', { name: '나중에 다시 시도' }));

    // then
    expect(onRetryLater).toHaveBeenCalledTimes(1);
  });

  it('실패 후 같은 답안이면 새 제출을 막고, 답안을 고치면 대체 안내와 함께 새로 제출한다', async () => {
    // given
    setScenario('accusationFail', true);
    renderHarness();
    await submitAnswer('마거릿', PARTIAL);
    await screen.findByText('채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.');
    setScenario('accusationFail', false);

    // then 같은 답안
    expect(screen.getByRole('button', { name: '제출' })).toBeDisabled();
    expect(screen.getByText(/채점에 실패한 답안과 같습니다/)).toBeInTheDocument();

    // when 답안을 고친다
    fireEvent.change(screen.getByLabelText('트릭 설명'), { target: { value: CORRECT } });
    fireEvent.click(screen.getByRole('button', { name: '제출' }));

    // then
    const confirm = screen.getByRole('group', { name: '제출 확인' });
    expect(confirm).toHaveTextContent('이전 실패한 채점은 다시 할 수 없게 됩니다.');

    // when
    fireEvent.click(within(confirm).getByRole('button', { name: '제출하기' }));

    // then 새 requestId로 연결 헤더와 함께 보내고, 이전 실패는 대체됨으로 바뀐다
    expect(await screen.findByText('새 답안으로 대체됨')).toBeInTheDocument();
    expect(posts).toHaveLength(2);
    expect(posts[1].body.requestId).not.toBe(posts[0].body.requestId);
    expect(posts[1].connectionId).not.toBeNull();
    expect(
      within(records()).queryAllByRole('button', { name: '같은 답안 다시 채점' }),
    ).toHaveLength(0);
  });

  it('마감 후에는 새 답안 입력을 막고 재채점만 보여 준다', async () => {
    // given 채점 실패 후 시간이 끝났다
    setScenario('accusationFail', true);
    await createAccusation(
      roomId,
      { requestId: ID(1), suspectId: 2, trickExplanation: PARTIAL },
      { isRetry: false },
    ).catch(() => undefined);
    const room = findMockRoom(roomId, userId)!;
    room.elapsedActiveMs = 20 * 60 * 1000;
    room.segmentStartedAtMs = currentTimeMs();
    saveMockRoomDb();

    // when
    renderHarness();

    // then
    expect(
      await screen.findByText(/제한시간이 끝나 새 답안을 제출할 수 없습니다/),
    ).toBeInTheDocument();
    expect(screen.queryByLabelText('트릭 설명')).not.toBeInTheDocument();
    expect(await screen.findByRole('button', { name: '같은 답안 다시 채점' })).toBeInTheDocument();
  });
});

describe('AccusationPanel 복원', () => {
  it('state.aiRequests에 채점 중 지목이 있으면 폴링을 이어 가 결과를 보여 준다', async () => {
    // given 다른 화면에서 보낸 지목이 채점 중이다 (상태 조회 두 번째에 끝난다)
    const room = findMockRoom(roomId, userId)!;
    acceptAccusation(room, {
      requestId: ID(1),
      suspectId: 2,
      trickExplanation: PARTIAL,
      pollsUntilDone: 2,
      failFirstAttempt: false,
    });
    saveMockRoomDb();

    // when
    renderHarness();

    // then
    expect((await screen.findAllByText('채점 중입니다…')).length).toBeGreaterThan(0);
    expect(await screen.findByText('방금 받은 결과', {}, { timeout: 6000 })).toBeInTheDocument();
    expect(requestedPaths).toContain(`GET /api/rooms/${roomId}/ai-requests/${ID(1)}`);
    expect(posts).toHaveLength(0);
  }, 10_000);

  it('state.aiRequests에 채점 실패 지목이 있으면 재채점 영역을 보여 준다', async () => {
    // given
    setScenario('accusationFail', true);
    await createAccusation(
      roomId,
      { requestId: ID(1), suspectId: 2, trickExplanation: PARTIAL },
      { isRetry: false },
    ).catch(() => undefined);
    posts.length = 0;

    // when
    renderHarness();

    // then
    const item = within(await screen.findByRole('list', { name: '지목 기록' })).getByRole(
      'listitem',
    );
    expect(
      await within(item).findByRole('button', { name: '같은 답안 다시 채점' }),
    ).toBeInTheDocument();
    expect(item).toHaveTextContent('마거릿');
    expect(posts).toHaveLength(0);
  });
});

describe('AccusationPanel 이번 지목 기록 스크롤', () => {
  /** scrollIntoView를 받은 요소와 위치. jsdom에는 scrollIntoView가 없어 목으로 둔다 */
  let scrolled: { tag: string; text: string; block: ScrollLogicalPosition | undefined }[] = [];
  const original = Element.prototype.scrollIntoView;

  beforeEach(() => {
    scrolled = [];
    Element.prototype.scrollIntoView = function (this: Element, options) {
      scrolled.push({
        tag: this.tagName,
        text: this.textContent ?? '',
        block: typeof options === 'object' ? options.block : undefined,
      });
    };
  });

  afterEach(() => {
    Element.prototype.scrollIntoView = original;
  });

  /** 모든 스크롤이 이번 지목 기록(li)을 창 맨 위에 맞췄는지 */
  function expectOnlyRecordAtTop(trick: string) {
    for (const call of scrolled) {
      expect(call).toMatchObject({ tag: 'LI', block: 'start' });
      expect(call.text).toContain(trick);
    }
  }

  /** "제출" → 확인 상자의 스크롤 기록을 지우고 → "제출하기" */
  async function submitAnswerAfterConfirmScroll() {
    await fillAnswer('마거릿', PARTIAL);
    const submit = screen.getByRole('button', { name: '제출' });
    await waitFor(() => expect(submit).toBeEnabled());
    fireEvent.click(submit);
    scrolled = [];
    fireEvent.click(screen.getByRole('button', { name: '제출하기' }));
  }

  it('"제출"을 누르면 확인 상자가 보이도록 창 아래로 스크롤한다', async () => {
    // given
    renderHarness();
    await fillAnswer('마거릿', PARTIAL);
    const submit = screen.getByRole('button', { name: '제출' });
    await waitFor(() => expect(submit).toBeEnabled());
    expect(scrolled).toEqual([]);

    // when
    fireEvent.click(submit);

    // then
    expect(scrolled).toHaveLength(1);
    expect(scrolled[0].block).toBe('end');
    expect(scrolled[0].text).toContain('제출하기');
    expect(posts).toHaveLength(0);
  });

  it('지목을 제출하면 이번 기록을 위에 맞추고, 창을 닫았다 열 때는 다시 옮기지 않는다', async () => {
    // given
    setScenario('accusationSlow', true);
    renderHarness();

    // when
    await submitAnswerAfterConfirmScroll();

    // then
    await waitFor(() => expect(scrolled).toHaveLength(1));
    expect(scrolled[0].text).toContain('채점 중입니다…');
    expectOnlyRecordAtTop(PARTIAL);

    // when 채점 중에 창을 다시 연다
    fireEvent.click(screen.getByRole('button', { name: '패널 닫기' }));
    fireEvent.click(screen.getByRole('button', { name: '지목 창 열기' }));
    await screen.findByRole('list', { name: '지목 기록' });

    // then
    expect(scrolled).toHaveLength(1);
  });

  it('결과를 받아도 아래 안내로 내려가지 않고 판정이 담긴 이번 기록에 맞춘다', async () => {
    // given
    renderHarness();

    // when
    await submitAnswerAfterConfirmScroll();
    await screen.findByText('방금 받은 결과');

    // then
    await waitFor(() => expect(scrolled.at(-1)?.text).toContain('방금 받은 결과'));
    expectOnlyRecordAtTop(PARTIAL);
  });

  it('"같은 답안 다시 채점"을 눌러도 이번 기록을 위에 맞춘다', async () => {
    // given
    setScenario('accusationFail', true);
    const view = renderHarness();
    await submitAnswer('마거릿', PARTIAL);
    await screen.findByText('채점에 실패했습니다. 지목 기회는 차감되지 않았습니다.');
    const base = currentTimeMs();
    setMockClock(() => base + 11_000);
    view.rerenderWith({ serverClockOffsetMs: 11_000 });
    const retry = screen.getByRole('button', { name: '같은 답안 다시 채점' });
    await waitFor(() => expect(retry).toBeEnabled());
    scrolled = [];

    // when
    fireEvent.click(retry);

    // then
    await waitFor(() => expect(scrolled.length).toBeGreaterThan(0));
    expect(scrolled[0].text).toContain('채점 중입니다…');
    expectOnlyRecordAtTop(PARTIAL);
  });
});
