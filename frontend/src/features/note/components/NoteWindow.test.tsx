import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { resetApiSession, setConnectionId } from '../../../shared/api/client';
import { MOCK_GAME_CONFIG } from '../../../mocks/data/clocktowerCase';
import { findMockRoom, setMockClock } from '../../../mocks/roomDb';
import { loginMockUser, resetLoginState } from '../../../mocks/testing/loginMockUser';
import { createRoom, startRoom } from '../../room/api';
import { createDraftStore } from '../draft';
import { useNote } from '../hooks/useNote';
import { NoteWindow } from './NoteWindow';

const START_TIME_MS = new Date('2026-10-05T14:00:00').getTime();
const MAX_LENGTH = MOCK_GAME_CONFIG.noteMaxLength;

let userId = 0;
let roomId = 0;

function Harness({ operable, onClose }: { operable: boolean; onClose: () => void }) {
  const note = useNote({ roomId, userId, operable });
  return <NoteWindow note={note} onClose={onClose} />;
}

function renderNote(options: { operable?: boolean; onClose?: () => void } = {}) {
  const queryClient = new QueryClient();
  return render(
    <QueryClientProvider client={queryClient}>
      <Harness operable={options.operable ?? true} onClose={options.onClose ?? (() => undefined)} />
    </QueryClientProvider>,
  );
}

async function findTextarea() {
  return (await screen.findByLabelText('노트 내용')) as HTMLTextAreaElement;
}

beforeEach(async () => {
  setMockClock(() => START_TIME_MS);
  resetLoginState();
  userId = (await loginMockUser()).userId;
  const room = await createRoom({ caseId: 1 });
  roomId = room.roomId;
  const connection = await startRoom(roomId);
  setConnectionId(connection.connectionId);
});

afterEach(() => {
  resetApiSession();
  setMockClock(null);
});

describe('NoteWindow', () => {
  it('글자 수를 현재 / 최대로 표시한다', async () => {
    // given / when
    renderNote();
    const textarea = await findTextarea();
    fireEvent.change(textarea, { target: { value: '단서 메모' } });

    // then
    expect(screen.getByText(`5 / ${MAX_LENGTH}`)).toBeInTheDocument();
    expect(screen.getByText('저장 안 된 변경 있음')).toBeInTheDocument();
  });

  it('operable이 false면 읽기 전용이고 이유를 보여 준다', async () => {
    // given / when
    renderNote({ operable: false });
    const textarea = await findTextarea();

    // then
    expect(textarea).toHaveAttribute('readonly');
    expect(screen.getByText('수사 진행 중에만 편집할 수 있습니다.')).toBeInTheDocument();
  });

  it('글자 수가 최대를 넘으면 입력을 자르지 않고 경고하며 저장하지 않는다', async () => {
    // given
    const onClose = vi.fn();
    renderNote({ onClose });
    const textarea = await findTextarea();
    const tooLong = 'a'.repeat(MAX_LENGTH + 1);

    // when
    fireEvent.change(textarea, { target: { value: tooLong } });
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));

    // then
    expect(textarea.value).toBe(tooLong);
    expect(
      screen.getByText(`${MAX_LENGTH}자를 넘어서 저장되지 않습니다. 내용을 줄여 주세요.`),
    ).toBeInTheDocument();
    expect(findMockRoom(roomId, userId)?.note.content).toBe('');
  });

  it('닫기 버튼을 누르면 저장 안 된 내용을 저장하고 창을 닫는다 (앞뒤 공백·줄바꿈 그대로)', async () => {
    // given
    const onClose = vi.fn();
    renderNote({ onClose });
    const textarea = await findTextarea();
    const raw = '  단서\n\n  ';

    // when
    fireEvent.change(textarea, { target: { value: raw } });
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));

    // then
    expect(onClose).toHaveBeenCalledTimes(1);
    await waitFor(() => expect(findMockRoom(roomId, userId)?.note.content).toBe(raw));
    await waitFor(() => expect(screen.getByText('저장됨')).toBeInTheDocument());
    expect(createDraftStore(userId, roomId).read()).toBeNull();
  });

  it('서버와 다른 이 기기의 초안이 있으면 별도로 보여 주고, 이어 쓰기를 고를 수 있다', async () => {
    // given
    createDraftStore(userId, roomId).write('이 기기에만 있는 메모', 0);

    // when
    renderNote();
    const textarea = await findTextarea();

    // then
    expect(screen.getByText('이 기기의 미저장 초안')).toBeInTheDocument();
    expect(screen.getByText('이 기기에만 있는 메모')).toBeInTheDocument();
    expect(textarea.value).toBe('');

    fireEvent.click(screen.getByRole('button', { name: '초안으로 이어 쓰기' }));
    expect(textarea.value).toBe('이 기기에만 있는 메모');
    expect(screen.queryByText('이 기기의 미저장 초안')).not.toBeInTheDocument();
  });

  it('409 충돌이면 서버 내용과 내 초안을 나란히 보여 주고, 서버 내용으로 바꾸기를 고를 수 있다', async () => {
    // given
    renderNote();
    const textarea = await findTextarea();
    const room = findMockRoom(roomId, userId)!;
    room.note = { content: '다른 화면의 내용', revision: 5, updatedAtMs: START_TIME_MS };

    // when
    fireEvent.change(textarea, { target: { value: '내 내용' } });
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));
    const serverButton = await screen.findByRole('button', { name: '서버 내용으로 바꾸기' });

    // then
    expect(screen.getByText('다른 화면의 내용')).toBeInTheDocument();
    expect(screen.getAllByText('내 내용').length).toBeGreaterThan(0);
    expect(screen.getByRole('button', { name: '내 초안으로 덮어쓰기' })).toBeInTheDocument();
    expect(room.note.content).toBe('다른 화면의 내용');

    fireEvent.click(serverButton);
    expect((await findTextarea()).value).toBe('다른 화면의 내용');
    expect(createDraftStore(userId, roomId).read()).toBeNull();
  });

  it('409 충돌에서 내 초안으로 덮어쓰기를 고르면 서버 revision으로 다시 저장한다', async () => {
    // given
    renderNote();
    const textarea = await findTextarea();
    const room = findMockRoom(roomId, userId)!;
    room.note = { content: '다른 화면의 내용', revision: 5, updatedAtMs: START_TIME_MS };
    fireEvent.change(textarea, { target: { value: '내 내용' } });
    fireEvent.click(screen.getByRole('button', { name: '닫기' }));

    // when
    fireEvent.click(await screen.findByRole('button', { name: '내 초안으로 덮어쓰기' }));

    // then
    await waitFor(() => expect(room.note.content).toBe('내 내용'));
    expect(room.note.revision).toBe(6);
  });
});
