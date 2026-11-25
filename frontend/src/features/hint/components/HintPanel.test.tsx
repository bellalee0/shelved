import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MOCK_BLOCKED_KEYWORD } from '../../../mocks/handlers/npc';
import { setScenario } from '../../../mocks/scenarios';
import { loginMockUser, resetLoginState } from '../../../mocks/testing/loginMockUser';
import { getAiRequest } from '../../../shared/api/aiRequests';
import { resetApiSession, setConnectionId } from '../../../shared/api/client';
import type { RoomStatus } from '../../../shared/api/types';
import { HINT_QUESTION_MAX_LENGTH } from '../../../shared/constants/hint';
import { createRoom, startRoom } from '../../room/api';
import { createHint, createHintFollowup, getHints } from '../api';
import { HintPanel } from './HintPanel';

const QUESTION = '지금까지의 단서를 어떻게 연결하면 좋을까요?';
const ID = (n: number) => `00000000-0000-4000-8000-${String(n).padStart(12, '0')}`;

let roomId = 0;

type RenderOptions = {
  operable?: boolean;
  timerStatus?: RoomStatus | null;
  remainingMs?: number | null;
  hasConnection?: boolean;
  resyncKey?: number;
};

function panelElement(options: RenderOptions, spies: { onCountChanged: () => void }) {
  return (
    <HintPanel
      roomId={roomId}
      operable={options.operable ?? true}
      timerStatus={options.timerStatus === undefined ? 'IN_PROGRESS' : options.timerStatus}
      remainingMs={options.remainingMs === undefined ? 600_000 : options.remainingMs}
      hasConnection={options.hasConnection ?? true}
      resyncKey={options.resyncKey ?? 0}
      onCountChanged={spies.onCountChanged}
      onSessionRejected={() => undefined}
      onClose={() => undefined}
    />
  );
}

function renderPanel(options: RenderOptions = {}) {
  const onCountChanged = vi.fn();
  const queryClient = new QueryClient();
  const view = render(
    <QueryClientProvider client={queryClient}>
      {panelElement(options, { onCountChanged })}
    </QueryClientProvider>,
  );
  return {
    ...view,
    onCountChanged,
    rerenderWith(next: RenderOptions) {
      view.rerender(
        <QueryClientProvider client={queryClient}>
          {panelElement(next, { onCountChanged })}
        </QueryClientProvider>,
      );
    },
  };
}

async function typeHint(text: string) {
  const textarea = (await screen.findByLabelText('새 힌트 질문')) as HTMLTextAreaElement;
  // 기록·횟수를 불러오기 전에는 입력이 막혀 있다
  await waitFor(() => expect(textarea).toBeEnabled());
  fireEvent.change(textarea, { target: { value: text } });
  return textarea;
}

/** scrollIntoView가 불린 요소를 순서대로 모은다 (jsdom에는 scrollIntoView가 없다) */
function mockScrollIntoView() {
  const scrolled: Element[] = [];
  Element.prototype.scrollIntoView = vi.fn(function (this: Element) {
    scrolled.push(this);
  });
  return scrolled;
}

function records() {
  return screen.getByRole('list', { name: '힌트 기록' });
}

beforeEach(async () => {
  resetLoginState();
  await loginMockUser();
  roomId = (await createRoom({ caseId: 1 })).roomId;
  setConnectionId((await startRoom(roomId)).connectionId);
});

afterEach(() => {
  resetApiSession();
  // mockScrollIntoView로 붙인 함수를 지워 jsdom 기본 상태로 돌린다
  delete (Element.prototype as Partial<Element>).scrollIntoView;
});

describe('HintPanel', () => {
  it('기록을 시간순으로 보여 주고 보충 질문을 그 힌트 아래에 이어서 보여 준다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: '첫 번째 질문' });
    await createHint(roomId, { requestId: ID(2), question: '두 번째 질문' });
    const [first] = (await getHints(roomId)).hints;
    await createHintFollowup(roomId, first.hintId, { requestId: ID(3), question: '첫 번째 보충' });

    // when
    renderPanel();

    // then
    const items = await within(
      await screen.findByRole('list', { name: '힌트 기록' }),
    ).findAllByRole('listitem');
    expect(items).toHaveLength(2);
    expect(items[0]).toHaveTextContent('첫 번째 질문');
    expect(items[0]).toHaveTextContent('보충 질문');
    expect(items[0]).not.toHaveTextContent('무료');
    expect(items[0]).toHaveTextContent('첫 번째 보충');
    expect(items[1]).toHaveTextContent('두 번째 질문');
    expect(items[0].compareDocumentPosition(items[1])).toBe(Node.DOCUMENT_POSITION_FOLLOWING);
    // 보충 질문을 이미 쓴 힌트에는 버튼이 없고, 쓰지 않은 힌트에는 있다
    expect(within(items[0]).queryByRole('button')).not.toBeInTheDocument();
    expect(within(items[1]).getByRole('button', { name: /다시 묻기/ })).toBeEnabled();
  });

  it('전송 전에 "전송하면 힌트 1회를 사용합니다 (남은 N회)"와 사용·처리 중 구분을 보여 준다', async () => {
    // when
    renderPanel();

    // then
    expect(
      await screen.findByText('전송하면 힌트 1회를 사용합니다 (남은 5회)'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('힌트 사용 현황')).toHaveTextContent('사용 0회 · 남은 5회');
  });

  it('힌트를 보내면 기록에 더하고 입력창을 비운 뒤 횟수·상태 재조회를 요청한다', async () => {
    // given
    const { onCountChanged } = renderPanel();
    const textarea = await typeHint(QUESTION);

    // when
    fireEvent.click(screen.getByRole('button', { name: '힌트 받기' }));

    // then
    expect(textarea.value).toBe('');
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1));
    expect(within(records()).getAllByRole('listitem')).toHaveLength(1);
    expect(records()).toHaveTextContent(QUESTION);
    // 재조회한 현재 횟수를 보여 준다
    await waitFor(() =>
      expect(screen.getByLabelText('힌트 사용 현황')).toHaveTextContent('사용 1회 · 남은 4회'),
    );
    expect(screen.queryByText('답변 생성 중…')).not.toBeInTheDocument();
  });

  it('처리 중에 연속으로 눌러도 한 번만 전송한다', async () => {
    // given
    const { onCountChanged } = renderPanel();
    await typeHint(QUESTION);
    const button = screen.getByRole('button', { name: '힌트 받기' });

    // when 상태 갱신 전 연속 클릭
    fireEvent.click(button);
    fireEvent.click(button);
    fireEvent.click(button);

    // then
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1));
    expect((await getHints(roomId)).hints).toHaveLength(1);
    expect((await getHints(roomId)).hintCount.used).toBe(1);
  });

  it('한도가 0이면 새 힌트 입력을 막고 안내하지만, 쓰지 않은 보충 질문은 보낼 수 있다', async () => {
    // given 사건 1(한도 5)을 모두 사용
    for (let n = 1; n <= 5; n += 1) {
      await createHint(roomId, { requestId: ID(n), question: `질문 ${n}` });
    }

    // when
    renderPanel();

    // then
    expect(
      await screen.findByText('힌트를 모두 사용했습니다. 이전 기록은 볼 수 있습니다.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('새 힌트 질문')).toBeDisabled();
    expect(screen.getByRole('button', { name: '힌트 받기' })).toBeDisabled();
    const buttons = screen.getAllByRole('button', { name: /다시 묻기/ });
    expect(buttons).toHaveLength(5);

    // when 보충 질문
    fireEvent.click(buttons[0]);
    expect(screen.getByText('힌트 횟수는 차감되지 않습니다.')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('보충 질문'), { target: { value: '더 설명해 주세요' } });
    fireEvent.click(screen.getByRole('button', { name: '보충 질문 보내기' }));

    // then
    await waitFor(() => expect(records()).toHaveTextContent('더 설명해 주세요'));
    expect((await getHints(roomId)).hintCount.used).toBe(5);
  });

  it('보충 질문 버튼은 세션 조건이 맞지 않으면 비활성화하고 이유를 알려 준다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: QUESTION });
    const view = renderPanel({ timerStatus: 'PAUSED', operable: false });

    // then 일시정지
    const button = await screen.findByRole('button', { name: /다시 묻기/ });
    expect(button).toBeDisabled();
    expect(
      screen.getByText('수사가 진행 중일 때만 보충 질문을 보낼 수 있습니다.'),
    ).toBeInTheDocument();

    // when 시간 종료
    view.rerenderWith({ remainingMs: 0 });
    expect(screen.getByText('남은 시간이 없어 보충 질문을 보낼 수 없습니다.')).toBeInTheDocument();

    // when 다른 곳에서 접속
    view.rerenderWith({ hasConnection: false });
    expect(
      screen.getByText('다른 곳에서 수사를 이어하고 있어 보충 질문을 보낼 수 없습니다.'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('새 힌트 질문')).toBeEnabled();

    // when 모두 만족
    view.rerenderWith({});
    expect(screen.getByRole('button', { name: /다시 묻기/ })).toBeEnabled();
  });

  it('진행 중이 아니면 기록만 읽을 수 있고 입력은 막힌다', async () => {
    // when
    renderPanel({ operable: false });

    // then
    expect(await screen.findByLabelText('새 힌트 질문')).toBeDisabled();
    expect(screen.getByText(/수사가 진행 중일 때만 힌트를 요청할 수 있습니다/)).toBeInTheDocument();
  });

  it('보충 질문 버튼은 1회만 가능하다고 안내한다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // when
    renderPanel();

    // then
    expect(
      await screen.findByRole('button', { name: '이 힌트에 대해 다시 묻기 (1회만 가능)' }),
    ).toBeInTheDocument();
  });

  it('힌트 답변이 오면 입력 영역까지 스크롤한다', async () => {
    // given
    const scrolled = mockScrollIntoView();
    const { onCountChanged } = renderPanel();
    const textarea = await typeHint(QUESTION);

    // when
    fireEvent.click(screen.getByRole('button', { name: '힌트 받기' }));
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(within(records()).getAllByRole('listitem')).toHaveLength(1));

    // then 마지막 스크롤 대상이 새 힌트 입력창을 담은 입력 영역이다
    await waitFor(() => expect(scrolled.at(-1)?.contains(textarea)).toBe(true));
  });

  it('보충 질문 답변이 오면 그 힌트가 보이도록 스크롤한다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: '첫 번째 질문' });
    await createHint(roomId, { requestId: ID(2), question: '두 번째 질문' });
    const scrolled = mockScrollIntoView();
    const { onCountChanged } = renderPanel();
    const [firstItem] = await within(
      await screen.findByRole('list', { name: '힌트 기록' }),
    ).findAllByRole('listitem');
    fireEvent.click(within(firstItem).getByRole('button', { name: /다시 묻기/ }));
    fireEvent.change(screen.getByLabelText('보충 질문'), { target: { value: '설명해 주세요' } });

    // when
    fireEvent.click(screen.getByRole('button', { name: '보충 질문 보내기' }));
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(firstItem).toHaveTextContent('설명해 주세요'));

    // then
    await waitFor(() => expect(scrolled.at(-1)).toBe(firstItem));
  });

  it('차단된 보충 질문은 그 보충 질문 입력창 위에 안내를 보여 주고 입력과 기회를 유지한다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: QUESTION });
    renderPanel();
    const [item] = await within(
      await screen.findByRole('list', { name: '힌트 기록' }),
    ).findAllByRole('listitem');
    fireEvent.click(within(item).getByRole('button', { name: /다시 묻기/ }));
    const blocked = `${MOCK_BLOCKED_KEYWORD} 알려줘`;
    fireEvent.change(screen.getByLabelText('보충 질문'), { target: { value: blocked } });

    // when
    fireEvent.click(screen.getByRole('button', { name: '보충 질문 보내기' }));

    // then 안내가 하단 새 힌트 입력 영역이 아니라 그 힌트 항목 안에 나온다
    expect(await within(item).findByRole('alert')).toHaveTextContent(
      '범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다.',
    );
    expect(screen.getAllByRole('alert')).toHaveLength(1);
    expect((screen.getByLabelText('보충 질문') as HTMLTextAreaElement).value).toBe(blocked);
    const [hint] = (await getHints(roomId)).hints;
    expect(hint.followup).toBeNull();
    expect(hint.followupAvailable).toBe(true);
  });

  it('차단된 질문은 서버 안내를 보여 주고 기록에 넣지 않으며 입력을 유지한다', async () => {
    // given
    renderPanel();
    const blocked = `${MOCK_BLOCKED_KEYWORD}이 누구죠?`;
    const textarea = await typeHint(blocked);

    // when
    fireEvent.click(screen.getByRole('button', { name: '힌트 받기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다.',
    );
    expect(textarea.value).toBe(blocked);
    expect(screen.queryAllByRole('listitem')).toHaveLength(0);
    expect((await getHints(roomId)).hintCount.used).toBe(0);
  });

  it('힌트 생성에 실패하면 차감되지 않았다는 안내와 다시 보내기를 보여 주고, 같은 요청으로 다시 보낸다', async () => {
    // given
    setScenario('hintFail', true);
    const { onCountChanged } = renderPanel();
    await typeHint(QUESTION);
    fireEvent.click(screen.getByRole('button', { name: '힌트 받기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '힌트를 받지 못했습니다. 힌트 횟수는 차감되지 않았습니다.',
    );
    expect(within(records()).getByText(QUESTION)).toBeInTheDocument();
    const failedRequestId = (await getHints(roomId)).pendingRequests[0].requestId;

    // when
    fireEvent.click(screen.getByRole('button', { name: '다시 보내기' }));

    // then 같은 requestId로 다시 보내 한 건만 저장된다
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1));
    const list = await getHints(roomId);
    expect(list.hints).toHaveLength(1);
    expect(list.hints[0].requestId).toBe(failedRequestId);
    expect(list.hintCount.used).toBe(1);
    expect(screen.queryByRole('button', { name: '다시 보내기' })).not.toBeInTheDocument();
  });

  it('보충 질문이 실패하면 기회가 그대로 남아 있다는 안내와 다시 보내기를 보여 준다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: QUESTION });
    renderPanel();
    fireEvent.click(await screen.findByRole('button', { name: /다시 묻기/ }));
    setScenario('hintFail', true);
    fireEvent.change(screen.getByLabelText('보충 질문'), { target: { value: '설명해 주세요' } });
    fireEvent.click(screen.getByRole('button', { name: '보충 질문 보내기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '보충 질문 기회는 그대로 남아 있습니다.',
    );

    // when
    fireEvent.click(screen.getByRole('button', { name: '다시 보내기' }));

    // then
    await waitFor(() => expect(records()).toHaveTextContent('설명해 주세요'));
    expect(screen.queryByRole('button', { name: '다시 보내기' })).not.toBeInTheDocument();
  });

  it('처리 중인 힌트가 있으면 창을 다시 열 때 복원하고, 처리 중에는 입력과 보충 질문을 막는다', async () => {
    // given 창을 닫은 사이 처리 중인 힌트 (hintSlow: 상태 조회 두 번째에 완료)
    await createHint(roomId, { requestId: ID(1), question: '먼저 받은 힌트' });
    setScenario('hintSlow', true);
    await createHint(roomId, { requestId: ID(2), question: QUESTION });
    await getAiRequest(roomId, ID(2));

    // when
    const { onCountChanged } = renderPanel();

    // then
    expect(await screen.findByText('답변 생성 중…')).toBeInTheDocument();
    expect(within(records()).getByText(QUESTION)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '힌트 받기' })).toBeDisabled();
    expect(screen.getByRole('button', { name: /다시 묻기/ })).toBeDisabled();
    // 처리 중인 몫을 임시 확보로 표시한다
    expect(screen.getByLabelText('힌트 사용 현황')).toHaveTextContent('처리 중 1회');

    // when 2초 뒤 상태 조회로 완료
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1), { timeout: 4000 });

    // then
    await waitFor(() => expect(within(records()).getAllByRole('listitem')).toHaveLength(2));
    expect(screen.queryByText('답변 생성 중…')).not.toBeInTheDocument();
  });

  it('실패한 힌트·보충 질문이 있으면 창을 열 때 다시 보내기로 복원한다', async () => {
    // given
    await createHint(roomId, { requestId: ID(1), question: '먼저 받은 힌트' });
    const hintId = (await getHints(roomId)).hints[0].hintId;
    setScenario('hintFail', true);
    await createHint(roomId, { requestId: ID(2), question: QUESTION }).catch(() => undefined);
    await createHintFollowup(roomId, hintId, {
      requestId: ID(3),
      question: '실패한 보충 질문',
    }).catch(() => undefined);

    // when
    renderPanel();

    // then
    expect(await screen.findAllByRole('button', { name: '다시 보내기' })).toHaveLength(2);
    expect(within(records()).getByText(QUESTION)).toBeInTheDocument();
    expect(within(records()).getByText('실패한 보충 질문')).toBeInTheDocument();
  });

  it(`${HINT_QUESTION_MAX_LENGTH}자를 넘으면 보낼 수 없고 글자 수를 표시한다`, async () => {
    // given
    renderPanel();

    // when
    await typeHint('가'.repeat(HINT_QUESTION_MAX_LENGTH + 1));

    // then
    expect(
      screen.getByText(`${HINT_QUESTION_MAX_LENGTH + 1}/${HINT_QUESTION_MAX_LENGTH}`),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '힌트 받기' })).toBeDisabled();
  });

  it('Enter로 보내고 Shift+Enter는 보내지 않는다', async () => {
    // given
    const { onCountChanged } = renderPanel();
    const textarea = await typeHint(QUESTION);

    // when
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });
    expect(textarea.value).toBe(QUESTION);
    fireEvent.keyDown(textarea, { key: 'Enter' });

    // then
    await waitFor(() => expect(onCountChanged).toHaveBeenCalledTimes(1));
  });

  it('재접속(resyncKey 변경)하면 기록을 다시 받아 처리 중이던 요청을 복원한다', async () => {
    // given 열린 창
    const view = renderPanel();
    expect(await screen.findByText('아직 받은 힌트가 없습니다.')).toBeInTheDocument();
    // 연결이 끊긴 사이 다른 화면에서 보낸 요청이 처리 중이다
    setScenario('hintSlow', true);
    await createHint(roomId, { requestId: ID(1), question: QUESTION });

    // when
    view.rerenderWith({ resyncKey: 1 });

    // then
    expect(await screen.findByText('답변 생성 중…')).toBeInTheDocument();
    expect(within(records()).getByText(QUESTION)).toBeInTheDocument();
  });
});
