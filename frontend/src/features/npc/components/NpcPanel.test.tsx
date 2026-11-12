import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MOCK_BLOCKED_KEYWORD } from '../../../mocks/handlers/npc';
import { setScenario } from '../../../mocks/scenarios';
import { loginMockUser, resetLoginState } from '../../../mocks/testing/loginMockUser';
import { getAiRequest } from '../../../shared/api/aiRequests';
import { resetApiSession, setConnectionId } from '../../../shared/api/client';
import { NPC_QUESTION_MAX_LENGTH } from '../../../shared/constants/npc';
import { createRoom, startRoom } from '../../room/api';
import { askNpc } from '../api';
import { NpcPanel, type NpcPanelSuspect } from './NpcPanel';

const TOM: NpcPanelSuspect = { suspectId: 3, name: '톰', canConverse: true };
const QUESTION = '어젯밤 9시에 어디에 있었나요?';

let roomId = 0;

function renderPanel(
  options: { suspect?: NpcPanelSuspect; operable?: boolean; queryClient?: QueryClient } = {},
) {
  const onAnswered = vi.fn();
  const onSessionRejected = vi.fn();
  const queryClient = options.queryClient ?? new QueryClient();
  const view = render(
    <QueryClientProvider client={queryClient}>
      <NpcPanel
        roomId={roomId}
        suspect={options.suspect ?? TOM}
        operable={options.operable ?? true}
        resyncKey={0}
        onAnswered={onAnswered}
        onSessionRejected={onSessionRejected}
        onClose={() => undefined}
      />
    </QueryClientProvider>,
  );
  return { ...view, onAnswered, onSessionRejected, queryClient };
}

async function typeQuestion(text: string) {
  const textarea = (await screen.findByLabelText('질문')) as HTMLTextAreaElement;
  fireEvent.change(textarea, { target: { value: text } });
  return textarea;
}

function conversation() {
  return screen.getByRole('list', { name: '톰와의 대화' });
}

beforeEach(async () => {
  resetLoginState();
  await loginMockUser();
  roomId = (await createRoom({ caseId: 1 })).roomId;
  setConnectionId((await startRoom(roomId)).connectionId);
});

afterEach(() => {
  resetApiSession();
});

describe('NpcPanel', () => {
  it('질문을 보내면 입력창을 비우고 질문·답변 말풍선을 보여 준 뒤 상태 재조회를 요청한다', async () => {
    // given
    const { onAnswered } = renderPanel();
    expect(await screen.findByText('아직 나눈 대화가 없습니다.')).toBeInTheDocument();
    const textarea = await typeQuestion(QUESTION);

    // when
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    // then
    expect(textarea.value).toBe('');
    await waitFor(() => expect(onAnswered).toHaveBeenCalledTimes(1));
    const bubbles = within(conversation()).getAllByRole('listitem');
    expect(bubbles).toHaveLength(2);
    expect(bubbles[0]).toHaveTextContent(QUESTION);
    expect(bubbles[1]).toHaveTextContent('톰');
    expect(screen.queryByText('답변 생성 중…')).not.toBeInTheDocument();
  });

  it('Enter로 보내고 Shift+Enter는 보내지 않는다', async () => {
    // given
    const { onAnswered } = renderPanel();
    const textarea = await typeQuestion(QUESTION);

    // when
    fireEvent.keyDown(textarea, { key: 'Enter', shiftKey: true });

    // then
    expect(textarea.value).toBe(QUESTION);

    // when
    fireEvent.keyDown(textarea, { key: 'Enter' });

    // then
    await waitFor(() => expect(onAnswered).toHaveBeenCalledTimes(1));
  });

  it('차단된 질문은 서버 안내를 보여 주고 기록에 넣지 않으며 입력을 되돌린다', async () => {
    // given
    renderPanel();
    const blocked = `${MOCK_BLOCKED_KEYWORD}은 누구인가요?`;
    const textarea = await typeQuestion(blocked);

    // when
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent(
      '범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다.',
    );
    expect(textarea.value).toBe(blocked);
    expect(within(conversation()).queryAllByRole('listitem')).toHaveLength(0);
  });

  it('답변 생성에 실패하면 안내와 다시 보내기를 보여 주고, 다시 보내면 답변을 받는다', async () => {
    // given
    setScenario('npcFail', true);
    const { onAnswered } = renderPanel();
    await typeQuestion(QUESTION);
    fireEvent.click(screen.getByRole('button', { name: '보내기' }));

    // then
    expect(await screen.findByRole('alert')).toHaveTextContent('NPC의 답변을 받지 못했습니다.');
    expect(within(conversation()).getByText(QUESTION)).toBeInTheDocument();

    // when
    fireEvent.click(screen.getByRole('button', { name: '다시 보내기' }));

    // then
    await waitFor(() => expect(onAnswered).toHaveBeenCalledTimes(1));
    expect(within(conversation()).getAllByRole('listitem')).toHaveLength(2);
    expect(screen.queryByRole('button', { name: '다시 보내기' })).not.toBeInTheDocument();
  });

  it('처리 중인 질문이 있으면 창을 열 때 답변 생성 중으로 복원하고, 완료되면 답변을 보여 준다', async () => {
    // given 창을 닫은 사이 처리 중인 질문 (npcSlow: 상태 조회 두 번째에 완료)
    setScenario('npcSlow', true);
    await askNpc(roomId, TOM.suspectId, {
      requestId: '5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11',
      question: QUESTION,
    });
    await getAiRequest(roomId, '5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11');

    // when
    const { onAnswered } = renderPanel();

    // then
    expect(await screen.findByText('답변 생성 중…')).toBeInTheDocument();
    expect(within(conversation()).getByText(QUESTION)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '보내기' })).toBeDisabled();

    // when 2초 뒤 상태 조회
    await waitFor(() => expect(onAnswered).toHaveBeenCalledTimes(1), { timeout: 4000 });

    // then
    expect(within(conversation()).getAllByRole('listitem')).toHaveLength(2);
    expect(screen.queryByText('답변 생성 중…')).not.toBeInTheDocument();
  });

  it('실패한 질문이 있으면 창을 열 때 다시 보내기로 복원한다', async () => {
    // given
    setScenario('npcFail', true);
    await askNpc(roomId, TOM.suspectId, {
      requestId: '5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11',
      question: QUESTION,
    }).catch(() => undefined);

    // when
    renderPanel();

    // then
    expect(await screen.findByRole('button', { name: '다시 보내기' })).toBeEnabled();
    expect(within(conversation()).getByText(QUESTION)).toBeInTheDocument();
  });

  it('대화할 수 없는 인물은 입력창 없이 안내만 보여 준다', async () => {
    // when
    renderPanel({ suspect: { suspectId: 4, name: '엘리스', canConverse: false } });

    // then
    expect(screen.getByText('이 인물과는 대화할 수 없습니다.')).toBeInTheDocument();
    expect(screen.queryByLabelText('질문')).not.toBeInTheDocument();
  });

  it('진행 중이 아니면 기록만 읽을 수 있고 입력은 막힌다', async () => {
    // when
    renderPanel({ operable: false });

    // then
    expect(await screen.findByLabelText('질문')).toBeDisabled();
    expect(screen.getByText(/수사가 진행 중일 때만 질문할 수 있습니다/)).toBeInTheDocument();
  });

  it(`${NPC_QUESTION_MAX_LENGTH}자를 넘으면 보낼 수 없고 글자 수를 표시한다`, async () => {
    // given
    renderPanel();

    // when
    await typeQuestion('가'.repeat(NPC_QUESTION_MAX_LENGTH + 1));

    // then
    expect(
      screen.getByText(`${NPC_QUESTION_MAX_LENGTH + 1}/${NPC_QUESTION_MAX_LENGTH}`),
    ).toBeInTheDocument();
    expect(screen.getByRole('button', { name: '보내기' })).toBeDisabled();
  });
});
