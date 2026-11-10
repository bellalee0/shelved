import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useEffect } from 'react';
import { http, HttpResponse } from 'msw';
import { createMemoryRouter, RouterProvider } from 'react-router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { routes } from '../app/routes';
import type { RoomMapResponse } from '../features/room/types';
import { EventBus } from '../game/EventBus';
import { GameEvents } from '../game/events';
import { server } from '../mocks/node';
import { findMockRoom, pauseMockRoom, seedMockRoom, startMockRoom } from '../mocks/roomDb';
import { loginMockUser, resetLoginState } from '../mocks/testing/loginMockUser';

// Phaser는 jsdom에서 그릴 수 없으므로, 받은 Props를 기록하고 마운트되면 MAP_READY를 보내는 가짜로 바꾼다.
// autoReady를 끄면 테스트가 직접 MAP_READY를 보낸다.
const mockPhaser = vi.hoisted(() => ({
  receivedMapData: [] as RoomMapResponse[],
  autoReady: true,
}));

vi.mock('../game/PhaserGame', () => ({
  PhaserGame: ({ mapData }: { mapData: RoomMapResponse }) => {
    mockPhaser.receivedMapData.push(mapData);
    useEffect(() => {
      if (mockPhaser.autoReady) {
        EventBus.emit(GameEvents.MAP_READY);
      }
    }, []);
    return <div data-testid="phaser-game-mock" />;
  },
}));

const inputEvents: boolean[] = [];
const recordInput = ({ enabled }: { enabled: boolean }) => inputEvents.push(enabled);
const requestedPaths: string[] = [];
const recordRequest = ({ request }: { request: Request }) =>
  requestedPaths.push(`${request.method} ${new URL(request.url).pathname}`);

function renderAt(path: string) {
  const router = createMemoryRouter(routes, { initialEntries: [path] });
  render(
    <QueryClientProvider
      client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}
    >
      <RouterProvider router={router} />
    </QueryClientProvider>,
  );
  return router;
}

async function enterRoom(status: 'READY' | 'PAUSED' | 'IN_PROGRESS', remainingMs?: number) {
  const user = await loginMockUser();
  const room = seedMockRoom(user.userId, { caseId: 1, status, remainingMs });
  const router = renderAt(`/rooms/${room.roomId}`);
  return { user, room, router };
}

async function startPlaying() {
  const entered = await enterRoom('READY');
  const startButton = await screen.findByRole('button', { name: '수사 시작' });
  await waitFor(() => expect(startButton).toBeEnabled());
  fireEvent.click(startButton);
  await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
  return entered;
}

beforeEach(() => {
  resetLoginState();
  mockPhaser.receivedMapData.length = 0;
  mockPhaser.autoReady = true;
  inputEvents.length = 0;
  requestedPaths.length = 0;
  EventBus.on(GameEvents.SET_INPUT_ENABLED, recordInput);
  server.events.on('request:start', recordRequest);
});

afterEach(() => {
  EventBus.off(GameEvents.SET_INPUT_ENABLED, recordInput);
  server.events.removeListener('request:start', recordRequest);
  vi.useRealTimers();
});

describe('게임 화면 입장 흐름', () => {
  it('READY면 수사 준비 화면을 보여 주고, 수사 시작 후 연결로 조작 API를 보낸다', async () => {
    // given
    const { room, user } = await enterRoom('READY');

    // when
    expect(await screen.findByText('수사 준비')).toBeInTheDocument();
    expect(screen.getByText(/시계탑이 있는 저택에서/)).toBeInTheDocument();
    const startButton = screen.getByRole('button', { name: '수사 시작' });
    await waitFor(() => expect(startButton).toBeEnabled());
    fireEvent.click(startButton);

    // then
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(findMockRoom(room.roomId, user.userId)?.status).toBe('IN_PROGRESS');
    expect(inputEvents.at(-1)).toBe(true);
    const roomPaths = requestedPaths.filter((path) => path.includes('/api/rooms/'));
    expect(roomPaths[0]).toBe(`POST /api/rooms/${room.roomId}/pause`);

    // when 연결 헤더가 필요한 물증 조사가 성공하면 connectionId를 보관한 것이다
    act(() => EventBus.emit(GameEvents.INTERACT_REQUESTED, { type: 'EVIDENCE', targetId: 1 }));

    // then
    expect(await screen.findByText(/책장 뒤에서 발견된/)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '서재의 작은 상자' })).toBeInTheDocument();
    expect(inputEvents.at(-1)).toBe(false);
  });

  it('MAP_READY 전에는 시작 버튼이 비활성이고 SET_INPUT_ENABLED를 보내지 않는다', async () => {
    // given
    mockPhaser.autoReady = false;
    await enterRoom('READY');
    const startButton = await screen.findByRole('button', { name: '수사 시작' });
    await screen.findByTestId('phaser-game-mock');

    // then
    expect(startButton).toBeDisabled();
    expect(inputEvents).toEqual([]);

    // when
    act(() => EventBus.emit(GameEvents.MAP_READY));

    // then
    expect(startButton).toBeEnabled();
    expect(inputEvents).toEqual([false]);
  });

  it('PAUSED면 복원 화면을 보여 주고 이어하기로 진행한다', async () => {
    // given
    const { room, user } = await enterRoom('PAUSED', 768_750);

    // when
    expect(await screen.findByText('수사를 이어서 진행합니다')).toBeInTheDocument();
    const dialog = screen.getByRole('dialog');
    expect(dialog).toHaveTextContent('남은 시간 12:48');
    expect(dialog).toHaveTextContent('남은 힌트 5 · 남은 지목 3');
    const resumeButton = screen.getByRole('button', { name: '수사 이어하기' });
    await waitFor(() => expect(resumeButton).toBeEnabled());
    fireEvent.click(resumeButton);

    // then
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(findMockRoom(room.roomId, user.userId)?.status).toBe('IN_PROGRESS');
    expect(screen.getByText('진행 중')).toBeInTheDocument();
  });

  it('진행 중 세션에 다시 들어오면 pause 후 복원 화면을 보여 준다 (새로고침)', async () => {
    // given
    const { room, user } = await enterRoom('IN_PROGRESS');

    // when
    await screen.findByText('수사를 이어서 진행합니다');

    // then
    expect(findMockRoom(room.roomId, user.userId)?.status).toBe('PAUSED');
  });

  it('종료 상태면 맵을 로딩하지 않고 결과 화면으로 이동한다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'CLEARED' });

    // when
    const router = renderAt(`/rooms/${room.roomId}`);

    // then
    expect(await screen.findByRole('heading', { name: '수사 결과' })).toBeInTheDocument();
    expect(router.state.location.pathname).toBe(`/rooms/${room.roomId}/result`);
    expect(mockPhaser.receivedMapData).toHaveLength(0);
  });

  it('남은 시간 없이 PAUSED(채점 대기)면 맵을 로딩하지 않고 채점 대기 화면을 보여 준다', async () => {
    // given
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, {
      caseId: 2,
      status: 'PAUSED',
      remainingMs: 0,
      pendingGradingStatus: 'PENDING',
    });

    // when
    renderAt(`/rooms/${room.roomId}`);

    // then
    expect(await screen.findByText('채점 결과를 기다리고 있습니다')).toBeInTheDocument();
    expect(screen.queryByTestId('phaser-game-mock')).not.toBeInTheDocument();
  });

  it('start가 409면 state를 다시 조회해 바뀐 상태(PAUSED)로 분기한다', async () => {
    // given 준비 화면을 보는 사이 다른 화면에서 시작했다가 멈췄다
    const { room } = await enterRoom('READY');
    const startButton = await screen.findByRole('button', { name: '수사 시작' });
    await waitFor(() => expect(startButton).toBeEnabled());
    startMockRoom(room);
    pauseMockRoom(room);

    // when
    fireEvent.click(startButton);

    // then
    expect(await screen.findByText('수사를 이어서 진행합니다')).toBeInTheDocument();
    expect(requestedPaths.filter((path) => path.endsWith('/state'))).toHaveLength(2);
  });

  it('입장 요청이 실패하면 오류와 다시 시도를 보여 준다', async () => {
    // given
    await loginMockUser();

    // when 없는(또는 남의) 세션
    renderAt('/rooms/999');

    // then
    expect(await screen.findByText('수사 화면을 열지 못했습니다')).toBeInTheDocument();
    expect(screen.getByRole('alert')).toHaveTextContent('세션이 존재하지 않습니다.');
    expect(screen.getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
  });

  it('맵 로드에 실패하면 다시 시도로 맵을 다시 마운트한다', async () => {
    // given
    mockPhaser.autoReady = false;
    await enterRoom('READY');
    await screen.findByTestId('phaser-game-mock');

    // when
    act(() => EventBus.emit(GameEvents.MAP_LOAD_FAILED));

    // then
    expect(screen.getByRole('alert')).toHaveTextContent('맵을 불러오지 못했습니다.');

    // when
    const mountCount = mockPhaser.receivedMapData.length;
    fireEvent.click(screen.getByRole('button', { name: '다시 시도' }));

    // then
    await waitFor(() => expect(mockPhaser.receivedMapData.length).toBeGreaterThan(mountCount));
    expect(screen.getByRole('button', { name: '수사 시작' })).toBeDisabled();
  });
});

describe('게임 화면 패널·창', () => {
  it('NPC를 고르면 이름과 준비 중 안내만 보여 주고 API를 호출하지 않는다', async () => {
    // given
    await startPlaying();
    const before = requestedPaths.length;

    // when
    act(() => EventBus.emit(GameEvents.INTERACT_REQUESTED, { type: 'SUSPECT', targetId: 3 }));

    // then
    expect(screen.getByRole('heading', { name: '톰' })).toBeInTheDocument();
    expect(screen.getByText('대화 기능은 준비 중입니다.')).toBeInTheDocument();
    expect(requestedPaths.slice(before).filter((path) => !path.endsWith('/heartbeat'))).toEqual([]);
  });

  it('사전 정보 패널을 열고 Esc로 닫으면 다시 이동을 허용한다', async () => {
    // given
    await startPlaying();

    // when
    fireEvent.click(screen.getByRole('button', { name: '사전 정보' }));

    // then
    expect(screen.getByRole('heading', { name: '사전 정보' })).toBeInTheDocument();
    expect(inputEvents.at(-1)).toBe(false);

    // when
    fireEvent.keyDown(window, { key: 'Escape' });

    // then
    expect(screen.queryByRole('heading', { name: '사전 정보' })).not.toBeInTheDocument();
    expect(inputEvents.at(-1)).toBe(true);
  });

  it('입력이 막혀 있는 동안 받은 INTERACT_REQUESTED는 무시한다', async () => {
    // given 준비 화면(오버레이)
    await enterRoom('READY');
    await screen.findByRole('button', { name: '수사 시작' });

    // when
    act(() => EventBus.emit(GameEvents.INTERACT_REQUESTED, { type: 'SUSPECT', targetId: 3 }));

    // then
    expect(screen.queryByText('대화 기능은 준비 중입니다.')).not.toBeInTheDocument();
  });
});

describe('게임 화면 하트비트', () => {
  it('하트비트 응답이 PAUSED면 복원 화면으로 바꾼다', async () => {
    // given
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { room } = await startPlaying();
    pauseMockRoom(room);

    // when
    await act(() => vi.advanceTimersByTimeAsync(5000));

    // then
    expect(await screen.findByText('수사를 이어서 진행합니다')).toBeInTheDocument();
  });

  it('CONNECTION_REPLACED면 교체 안내를 띄우고, 이 화면에서 계속하기는 재접속 흐름을 따른다', async () => {
    // given
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { room, user } = await startPlaying();
    findMockRoom(room.roomId, user.userId)!.connectionId = 'other-tab';

    // when
    await act(() => vi.advanceTimersByTimeAsync(5000));

    // then
    expect(await screen.findByText('다른 화면에서 수사를 이어하고 있습니다')).toBeInTheDocument();
    expect(inputEvents.at(-1)).toBe(false);
    const heartbeatCount = requestedPaths.filter((path) => path.endsWith('/heartbeat')).length;
    await act(() => vi.advanceTimersByTimeAsync(15000));
    expect(requestedPaths.filter((path) => path.endsWith('/heartbeat'))).toHaveLength(
      heartbeatCount,
    );

    // when
    fireEvent.click(screen.getByRole('button', { name: '이 화면에서 계속하기' }));

    // then pause로 다른 화면의 연결을 끊고 복원 화면을 보여 준다
    expect(await screen.findByText('수사를 이어서 진행합니다')).toBeInTheDocument();
    expect(findMockRoom(room.roomId, user.userId)?.status).toBe('PAUSED');
  });
});

describe('저장 후 나가기', () => {
  it('pause 후 PAUSED를 확인하고 메인으로 이동한다', async () => {
    // given
    const { room, user, router } = await startPlaying();

    // when
    fireEvent.click(screen.getByRole('button', { name: '저장 후 나가기' }));

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(findMockRoom(room.roomId, user.userId)?.status).toBe('PAUSED');
  });

  it('pause가 실패하면 다시 시도를 보여 주고 다시 시도하면 나간다', async () => {
    // given
    const { router } = await startPlaying();
    server.use(
      http.post(
        `${import.meta.env.VITE_API_BASE_URL}/api/rooms/:roomId/pause`,
        () => HttpResponse.error(),
        { once: true },
      ),
    );

    // when
    fireEvent.click(screen.getByRole('button', { name: '저장 후 나가기' }));

    // then
    const retry = await screen.findByRole('button', { name: '다시 시도' });
    expect(router.state.location.pathname).not.toBe('/');

    // when
    fireEvent.click(retry);

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
  });
});

describe('게임 화면 개인 노트', () => {
  it('노트 창은 오른쪽 패널과 동시에 열리고, Esc는 최근 창부터 하나씩 닫는다', async () => {
    // given
    await startPlaying();

    // when
    fireEvent.click(screen.getByRole('button', { name: '노트' }));
    fireEvent.click(screen.getByRole('button', { name: '사전 정보' }));

    // then
    expect(screen.getByRole('region', { name: '개인 노트' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: '사전 정보' })).toBeInTheDocument();

    // when
    fireEvent.keyDown(window, { key: 'Escape' });

    // then
    expect(screen.queryByRole('heading', { name: '사전 정보' })).not.toBeInTheDocument();
    expect(screen.getByRole('region', { name: '개인 노트' })).toBeInTheDocument();

    // when
    fireEvent.keyDown(window, { key: 'Escape' });

    // then
    expect(screen.queryByRole('region', { name: '개인 노트' })).not.toBeInTheDocument();
    expect(inputEvents.at(-1)).toBe(true);
  });

  it('노트 창이 열려 있으면 이동은 막히고 노트 자동 저장은 된다', async () => {
    // given
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { room, user } = await startPlaying();
    fireEvent.click(screen.getByRole('button', { name: '노트' }));
    const textarea = await screen.findByLabelText('노트 내용');

    // when
    fireEvent.change(textarea, { target: { value: '9시 15분 메모가 수상하다' } });
    await act(() => vi.advanceTimersByTimeAsync(3000));

    // then
    expect(inputEvents.at(-1)).toBe(false);
    await waitFor(() =>
      expect(findMockRoom(room.roomId, user.userId)?.note.content).toBe('9시 15분 메모가 수상하다'),
    );
    expect(screen.getByRole('region', { name: '개인 노트' })).toBeInTheDocument();
  });

  it('저장 후 나가기에서 노트 저장이 실패하면 다시 시도 / 그래도 나가기를 보여 준다', async () => {
    // given
    const { router, room, user } = await startPlaying();
    fireEvent.click(screen.getByRole('button', { name: '노트' }));
    const textarea = await screen.findByLabelText('노트 내용');
    server.use(
      http.put(`${import.meta.env.VITE_API_BASE_URL}/api/rooms/:roomId/notes/me`, () =>
        HttpResponse.error(),
      ),
    );
    fireEvent.change(textarea, { target: { value: '저장 안 될 메모' } });

    // when
    fireEvent.click(screen.getByRole('button', { name: '저장 후 나가기' }));

    // then
    const exitDialog = await screen.findByRole('dialog', { name: '저장 후 나가기' });
    expect(await within(exitDialog).findByText('노트를 저장하지 못했습니다.')).toBeInTheDocument();
    expect(within(exitDialog).getByRole('button', { name: '다시 시도' })).toBeInTheDocument();
    expect(router.state.location.pathname).not.toBe('/');

    // when
    fireEvent.click(screen.getByRole('button', { name: '그래도 나가기 (이 기기에 초안 보관)' }));

    // then
    await waitFor(() => expect(router.state.location.pathname).toBe('/'));
    expect(findMockRoom(room.roomId, user.userId)?.status).toBe('PAUSED');
  });

  it('다른 화면이 노트를 저장한 뒤 이 화면에서 계속하면 서버의 최신 노트를 다시 불러온다', async () => {
    // given 노트를 한 번 불러온 뒤 다른 화면이 연결을 가져가 노트를 저장했다
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { room, user } = await startPlaying();
    fireEvent.click(screen.getByRole('button', { name: '노트' }));
    expect(await screen.findByLabelText('노트 내용')).toHaveValue('');
    const mockRoom = findMockRoom(room.roomId, user.userId)!;
    mockRoom.connectionId = 'other-tab';
    mockRoom.note = {
      ...mockRoom.note,
      content: '다른 탭 메모',
      revision: mockRoom.note.revision + 1,
    };
    await act(() => vi.advanceTimersByTimeAsync(5000));
    await screen.findByText('다른 화면에서 수사를 이어하고 있습니다');
    const noteFetches = () => requestedPaths.filter((path) => path.endsWith('/notes/me')).length;
    expect(noteFetches()).toBe(1);

    // when
    fireEvent.click(screen.getByRole('button', { name: '이 화면에서 계속하기' }));
    const resumeButton = await screen.findByRole('button', { name: '수사 이어하기' });
    await waitFor(() => expect(resumeButton).toBeEnabled());
    fireEvent.click(resumeButton);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // then
    expect(await screen.findByLabelText('노트 내용')).toHaveValue('다른 탭 메모');
    expect(noteFetches()).toBe(2);
  });

  it('하트비트로 일시정지된 뒤 이어하면 서버의 최신 노트를 다시 불러온다', async () => {
    // given
    vi.useFakeTimers({ shouldAdvanceTime: true });
    const { room, user } = await startPlaying();
    fireEvent.click(screen.getByRole('button', { name: '노트' }));
    expect(await screen.findByLabelText('노트 내용')).toHaveValue('');
    const mockRoom = findMockRoom(room.roomId, user.userId)!;
    pauseMockRoom(mockRoom);
    mockRoom.note = {
      ...mockRoom.note,
      content: '다른 탭 메모',
      revision: mockRoom.note.revision + 1,
    };
    await act(() => vi.advanceTimersByTimeAsync(5000));
    const resumeButton = await screen.findByRole('button', { name: '수사 이어하기' });

    // when
    fireEvent.click(resumeButton);
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());

    // then
    expect(await screen.findByLabelText('노트 내용')).toHaveValue('다른 탭 메모');
  });

  it('복원 화면은 이 기기에 저장되지 않은 노트 초안이 있으면 알려 준다', async () => {
    // given 이전 접속에서 저장하지 못한 초안 (키 형식: features/note/draft.ts)
    const user = await loginMockUser();
    const room = seedMockRoom(user.userId, { caseId: 1, status: 'PAUSED', remainingMs: 600_000 });
    localStorage.setItem(
      `shelved:note-draft:${user.userId}:${room.roomId}`,
      JSON.stringify({ content: '저장 못 한 초안', baseRevision: 0, savedAt: Date.now() }),
    );

    // when
    renderAt(`/rooms/${room.roomId}`);

    // then
    expect(await screen.findByText('수사를 이어서 진행합니다')).toBeInTheDocument();
    expect(
      await screen.findByText('이 기기에 저장되지 않은 노트 초안이 있습니다.'),
    ).toBeInTheDocument();
  });
});
