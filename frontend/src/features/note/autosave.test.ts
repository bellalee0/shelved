import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../../shared/api/apiError';
import { createAutosave } from './autosave';
import { createDraftStore, noteDraftKey } from './draft';
import type { NoteResponse } from './types';

const DELAY = 2000;
const USER_ID = 7;
const ROOM_ID = 3;

function response(content: string, revision: number): NoteResponse {
  return { content, revision, updatedAt: '', maxLength: 20, editable: true };
}

function apiError(status: number, code: string, detail: unknown = null) {
  return new ApiError({ status, message: '실패', code, detail, isNetworkError: false });
}

type Deferred = {
  content: string;
  revision: number;
  resolve: () => void;
  reject: (error: unknown) => void;
};

/** 호출을 직접 끝낼 수 있는 가짜 저장 함수. 호출 기록을 calls에 남긴다 */
function createFakeSave() {
  const calls: Deferred[] = [];
  const save = vi.fn((content: string, revision: number) => {
    return new Promise<NoteResponse>((resolve, reject) => {
      calls.push({
        content,
        revision,
        resolve: () => resolve(response(content, revision + 1)),
        reject,
      });
    });
  });
  return { save, calls };
}

function setup(options: { operable?: boolean; onSessionRejected?: (code: string) => void } = {}) {
  const fake = createFakeSave();
  const draftStore = createDraftStore(USER_ID, ROOM_ID);
  const autosave = createAutosave({
    save: fake.save,
    delayMs: DELAY,
    draftStore,
    onSessionRejected: options.onSessionRejected,
  });
  autosave.init({ content: '처음', revision: 4, maxLength: 20 });
  autosave.setOperable(options.operable ?? true);
  return { ...fake, autosave, draftStore };
}

/** 마이크로태스크를 비워 await 이후 코드가 실행되게 한다 */
async function settle() {
  await vi.advanceTimersByTimeAsync(0);
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
});

describe('노트 자동 저장', () => {
  it('입력이 멈추고 지정한 시간이 지나면 한 번 저장한다', async () => {
    // given
    const { autosave, save } = setup();

    // when
    autosave.setContent('처음 내용');
    await vi.advanceTimersByTimeAsync(DELAY - 1);
    const beforeDelay = save.mock.calls.length;
    await vi.advanceTimersByTimeAsync(1);

    // then
    expect(beforeDelay).toBe(0);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('처음 내용', 4);
  });

  it('연속으로 입력하면 마지막 내용만 저장한다', async () => {
    // given
    const { autosave, save } = setup();

    // when
    autosave.setContent('a');
    await vi.advanceTimersByTimeAsync(1000);
    autosave.setContent('ab');
    await vi.advanceTimersByTimeAsync(1000);
    autosave.setContent('abc');
    await vi.advanceTimersByTimeAsync(DELAY);

    // then
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('abc', 4);
  });

  it('저장 중에 입력이 바뀌면 동시에 보내지 않고 끝난 뒤 새 revision으로 최신 내용을 저장한다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('첫째');
    await vi.advanceTimersByTimeAsync(DELAY);

    // when
    autosave.setContent('첫째 둘째');
    await vi.advanceTimersByTimeAsync(DELAY * 2);
    const whileSaving = save.mock.calls.length;
    calls[0].resolve();
    await settle();

    // then
    expect(whileSaving).toBe(1);
    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith('첫째 둘째', 5);
    expect(autosave.getState().status).toBe('SAVING');
    calls[1].resolve();
    await settle();
    expect(autosave.getState().status).toBe('SAVED');
    expect(autosave.getState().revision).toBe(6);
  });

  it('네트워크 실패 후에도 입력을 유지하고, 자동으로 다시 보내지 않으며, 다음 입력으로 재시도한다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('내용');
    await vi.advanceTimersByTimeAsync(DELAY);

    // when
    calls[0].reject(
      new ApiError({
        status: null,
        message: '끊김',
        code: null,
        detail: null,
        isNetworkError: true,
      }),
    );
    await vi.advanceTimersByTimeAsync(DELAY * 5);

    // then
    expect(autosave.getState().status).toBe('FAILED');
    expect(autosave.getState().content).toBe('내용');
    expect(save).toHaveBeenCalledTimes(1);

    autosave.setContent('내용 더');
    await vi.advanceTimersByTimeAsync(DELAY);
    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith('내용 더', 4);
  });

  it('다시 시도 버튼(retry)으로 같은 내용을 다시 보낸다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('내용');
    await vi.advanceTimersByTimeAsync(DELAY);
    calls[0].reject(apiError(500, 'INTERNAL_SERVER_ERROR'));
    await settle();

    // when
    autosave.retry();
    await settle();

    // then
    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith('내용', 4);
  });

  it('세션 상태 때문에 거부되면 저장 불가로 두고 알리며 operable이 돌아올 때까지 보내지 않는다', async () => {
    // given
    const onSessionRejected = vi.fn();
    const { autosave, save, calls, draftStore } = setup({ onSessionRejected });
    autosave.setContent('내용');
    await vi.advanceTimersByTimeAsync(DELAY);

    // when
    calls[0].reject(apiError(409, 'ROOM_TIME_EXPIRED'));
    await settle();
    autosave.setContent('내용 더');
    await vi.advanceTimersByTimeAsync(DELAY * 3);

    // then
    expect(onSessionRejected).toHaveBeenCalledWith('ROOM_TIME_EXPIRED');
    expect(autosave.getState().status).toBe('BLOCKED');
    expect(save).toHaveBeenCalledTimes(1);
    expect(draftStore.read()?.content).toBe('내용 더');

    // operable이 꺼졌다 켜지면 한 번 저장한다
    autosave.setOperable(false);
    autosave.setOperable(true);
    await settle();
    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith('내용 더', 4);
  });

  it('operable이 false인 동안은 보내지 않고, true가 되면 저장 안 된 내용을 한 번 저장한다', async () => {
    // given
    const { autosave, save } = setup({ operable: false });

    // when
    autosave.setContent('내용');
    await vi.advanceTimersByTimeAsync(DELAY * 3);
    const whileBlocked = save.mock.calls.length;
    autosave.setOperable(true);
    await settle();

    // then
    expect(whileBlocked).toBe(0);
    expect(save).toHaveBeenCalledTimes(1);
    expect(save).toHaveBeenCalledWith('내용', 4);
  });

  it('글자 수가 maxLength를 넘으면 입력을 자르지 않고 저장도 보내지 않는다', async () => {
    // given
    const { autosave, save } = setup();
    const tooLong = 'a'.repeat(21);

    // when
    autosave.setContent(tooLong);
    await vi.advanceTimersByTimeAsync(DELAY * 2);

    // then
    expect(autosave.getState().content).toBe(tooLong);
    expect(autosave.getState().status).toBe('TOO_LONG');
    expect(save).not.toHaveBeenCalled();
  });

  it('409 충돌이면 자동으로 덮어쓰지 않고 서버 내용과 내 내용을 모두 남긴다', async () => {
    // given
    const { autosave, save, calls, draftStore } = setup();
    autosave.setContent('내 내용');
    await vi.advanceTimersByTimeAsync(DELAY);

    // when
    calls[0].reject(
      apiError(409, 'NOTE_REVISION_CONFLICT', { currentRevision: 9, currentContent: '서버 내용' }),
    );
    await vi.advanceTimersByTimeAsync(DELAY * 3);

    // then
    const state = autosave.getState();
    expect(state.status).toBe('CONFLICT');
    expect(state.conflict).toEqual({ currentRevision: 9, currentContent: '서버 내용' });
    expect(state.content).toBe('내 내용');
    expect(draftStore.read()?.content).toBe('내 내용');
    expect(save).toHaveBeenCalledTimes(1);
  });

  it('충돌에서 "서버 내용으로 바꾸기"를 고르면 서버 내용을 채택하고 초안을 지운다', async () => {
    // given
    const { autosave, calls, draftStore } = setup();
    autosave.setContent('내 내용');
    await vi.advanceTimersByTimeAsync(DELAY);
    calls[0].reject(
      apiError(409, 'NOTE_REVISION_CONFLICT', { currentRevision: 9, currentContent: '서버 내용' }),
    );
    await settle();

    // when
    autosave.resolveWithServer();

    // then
    const state = autosave.getState();
    expect(state.content).toBe('서버 내용');
    expect(state.revision).toBe(9);
    expect(state.status).toBe('SAVED');
    expect(draftStore.read()).toBeNull();
  });

  it('충돌에서 "내 초안으로 덮어쓰기"를 고르면 currentRevision으로 내 내용을 다시 저장한다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('내 내용');
    await vi.advanceTimersByTimeAsync(DELAY);
    calls[0].reject(
      apiError(409, 'NOTE_REVISION_CONFLICT', { currentRevision: 9, currentContent: '서버 내용' }),
    );
    await settle();

    // when
    autosave.overwriteWithMine();
    await settle();

    // then
    expect(save).toHaveBeenCalledTimes(2);
    expect(save).toHaveBeenLastCalledWith('내 내용', 9);
  });
});

describe('flush', () => {
  it('저장할 것이 없으면 NO_CHANGES를 돌려준다', async () => {
    // given
    const { autosave, save } = setup();

    // when
    const result = await autosave.flush();

    // then
    expect(result).toBe('NO_CHANGES');
    expect(save).not.toHaveBeenCalled();
  });

  it('대기 중인 자동 저장을 즉시 실행하고 성공하면 SAVED를 돌려준다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('내용');

    // when
    const promise = autosave.flush();
    expect(save).toHaveBeenCalledTimes(0);
    await settle();
    calls[0].resolve();
    const result = await promise;

    // then
    expect(save).toHaveBeenCalledTimes(1);
    expect(result).toBe('SAVED');
  });

  it('진행 중인 저장이 끝나길 기다린 뒤 최신 내용까지 저장하고, 중복해서 보내지 않는다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('하나');
    await vi.advanceTimersByTimeAsync(DELAY);
    autosave.setContent('하나 둘');

    // when
    const promise = autosave.flush();
    await settle();
    const whileSaving = save.mock.calls.length;
    calls[0].resolve();
    await settle();
    calls[1].resolve();
    const result = await promise;

    // then
    expect(whileSaving).toBe(1);
    expect(save).toHaveBeenCalledTimes(2);
    expect(result).toBe('SAVED');
  });

  it('저장이 실패하면 FAILED를 돌려준다', async () => {
    // given
    const { autosave, calls } = setup();
    autosave.setContent('내용');

    // when
    const promise = autosave.flush();
    await settle();
    calls[0].reject(apiError(500, 'INTERNAL_SERVER_ERROR'));
    const result = await promise;

    // then
    expect(result).toBe('FAILED');
    expect(autosave.getState().content).toBe('내용');
  });

  it('충돌·저장 불가 상태에서는 보내지 않고 FAILED를 돌려준다', async () => {
    // given
    const { autosave, save } = setup({ operable: false });
    autosave.setContent('내용');

    // when
    const result = await autosave.flush();

    // then
    expect(result).toBe('FAILED');
    expect(save).not.toHaveBeenCalled();
  });

  it('앞뒤 공백·줄바꿈이 있는 내용도 그대로 전송한다', async () => {
    // given
    const { autosave, save } = setup();
    const raw = '  첫 줄\n\n둘째 줄  \n';

    // when
    autosave.setContent(raw);
    void autosave.flush();
    await settle();

    // then
    expect(save).toHaveBeenCalledWith(raw, 4);
  });
});

describe('로컬 초안', () => {
  it('키는 사용자·세션별이다', () => {
    // given / when / then
    expect(noteDraftKey(7, 3)).toBe('shelved:note-draft:7:3');
    expect(noteDraftKey(8, 3)).not.toBe(noteDraftKey(7, 3));
    expect(noteDraftKey(7, 4)).not.toBe(noteDraftKey(7, 3));
  });

  it('입력하면 초안을 보관하고, 저장에 성공해 서버 내용과 같아지면 지운다', async () => {
    // given
    const { autosave, draftStore, calls } = setup();

    // when
    autosave.setContent('내용');
    const whileDirty = draftStore.read();
    await vi.advanceTimersByTimeAsync(DELAY);
    calls[0].resolve();
    await settle();

    // then
    expect(whileDirty).toMatchObject({ content: '내용', baseRevision: 4 });
    expect(draftStore.read()).toBeNull();
  });

  it('처음 불러올 때 서버와 다른 초안이 있으면 별도로 보여 주고 서버 내용을 덮어쓰지 않는다', () => {
    // given
    createDraftStore(USER_ID, ROOM_ID).write('이 기기 초안', 4);

    // when
    const { autosave } = setup();

    // then
    const state = autosave.getState();
    expect(state.content).toBe('처음');
    expect(state.pendingDraft?.content).toBe('이 기기 초안');
  });

  it('서버 내용과 같은 초안은 지우고 보여 주지 않는다', () => {
    // given
    createDraftStore(USER_ID, ROOM_ID).write('처음', 4);

    // when
    const { autosave, draftStore } = setup();

    // then
    expect(autosave.getState().pendingDraft).toBeNull();
    expect(draftStore.read()).toBeNull();
  });

  it('"초안으로 이어 쓰기"는 초안 내용을 입력창에 넣고 서버 revision으로 저장한다', async () => {
    // given
    createDraftStore(USER_ID, ROOM_ID).write('이 기기 초안', 2);
    const { autosave, save } = setup();

    // when
    autosave.continueWithDraft();
    await vi.advanceTimersByTimeAsync(DELAY);

    // then
    expect(autosave.getState().pendingDraft).toBeNull();
    expect(save).toHaveBeenCalledWith('이 기기 초안', 4);
  });

  it('초안을 이어 쓸지 버릴지 고르기 전에는 입력을 무시하고 저장소의 초안을 유지한다', async () => {
    // given
    createDraftStore(USER_ID, ROOM_ID).write('이 기기 초안', 4);
    const { autosave, save, draftStore } = setup();

    // when
    autosave.setContent('새 입력');
    await vi.advanceTimersByTimeAsync(DELAY * 2);

    // then
    expect(autosave.getState().content).toBe('처음');
    expect(autosave.getState().pendingDraft?.content).toBe('이 기기 초안');
    expect(draftStore.read()?.content).toBe('이 기기 초안');
    expect(save).not.toHaveBeenCalled();
  });

  it('"초안 버리기"는 초안을 지운다', () => {
    // given
    createDraftStore(USER_ID, ROOM_ID).write('이 기기 초안', 4);
    const { autosave, draftStore } = setup();

    // when
    autosave.discardDraft();

    // then
    expect(autosave.getState().pendingDraft).toBeNull();
    expect(draftStore.read()).toBeNull();
  });

  it('localStorage 접근이 실패해도 노트 저장은 동작한다', async () => {
    // given
    const denied = new Error('denied');
    vi.spyOn(Storage.prototype, 'getItem').mockImplementation(() => {
      throw denied;
    });
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => {
      throw denied;
    });
    vi.spyOn(Storage.prototype, 'removeItem').mockImplementation(() => {
      throw denied;
    });
    const { autosave, save, calls } = setup();

    // when
    autosave.setContent('내용');
    await vi.advanceTimersByTimeAsync(DELAY);
    calls[0].resolve();
    await settle();

    // then
    expect(save).toHaveBeenCalledWith('내용', 4);
    expect(autosave.getState().status).toBe('SAVED');
    vi.restoreAllMocks();
  });
});

describe('서버 노트로 다시 맞추기 (재접속)', () => {
  it('다시 받는 동안에는 불러오는 중으로 보이고 입력을 받지 않는다', async () => {
    // given
    const { autosave, save } = setup();

    // when
    void autosave.beginResync();
    autosave.setContent('다시 받는 중 입력');
    await vi.advanceTimersByTimeAsync(DELAY);

    // then
    expect(autosave.getState().loaded).toBe(false);
    expect(autosave.getState().status).toBe('LOADING');
    expect(autosave.getState().content).toBe('처음');
    expect(save).not.toHaveBeenCalled();
  });

  it('미저장 내용이 없으면 서버 내용과 revision으로 바꾼다', async () => {
    // given
    const { autosave, save, draftStore } = setup();
    void autosave.beginResync();

    // when 다른 탭이 저장한 내용
    autosave.resync({ content: '다른 탭 내용', revision: 7, maxLength: 20 });
    await settle();

    // then
    const state = autosave.getState();
    expect(state.loaded).toBe(true);
    expect(state.content).toBe('다른 탭 내용');
    expect(state.revision).toBe(7);
    expect(state.pendingDraft).toBeNull();
    expect(state.status).toBe('SAVED');
    expect(draftStore.read()).toBeNull();

    // when 이후 입력은 새 revision으로 저장한다
    autosave.setContent('이어서 입력');
    await vi.advanceTimersByTimeAsync(DELAY);

    // then
    expect(save).toHaveBeenLastCalledWith('이어서 입력', 7);
  });

  it('미저장 입력이 있으면 초안으로 남기고 서버 내용을 기준으로 초안 선택을 띄운다', async () => {
    // given 입력 후 자동 저장이 예약된 상태
    const { autosave, save, draftStore } = setup();
    autosave.setContent('내 입력');
    void autosave.beginResync();

    // when
    autosave.resync({ content: '다른 탭 내용', revision: 7, maxLength: 20 });
    await settle();
    await vi.advanceTimersByTimeAsync(DELAY);

    // then 예약된 저장은 취소되고, 선택 전에는 입력을 받지 않는다
    const state = autosave.getState();
    expect(save).not.toHaveBeenCalled();
    expect(state.content).toBe('다른 탭 내용');
    expect(state.revision).toBe(7);
    expect(state.pendingDraft?.content).toBe('내 입력');
    expect(draftStore.read()?.content).toBe('내 입력');
    autosave.setContent('선택 전 입력');
    expect(autosave.getState().content).toBe('다른 탭 내용');

    // when 이어 쓰기를 고르면 새 revision으로 저장한다
    autosave.continueWithDraft();
    await vi.advanceTimersByTimeAsync(DELAY);

    // then
    expect(save).toHaveBeenCalledWith('내 입력', 7);
  });

  it('남아 있던 초안 선택이 서버 내용과 같아지면 선택을 닫는다', async () => {
    // given
    createDraftStore(USER_ID, ROOM_ID).write('같은 내용', 4);
    const { autosave, draftStore } = setup();
    void autosave.beginResync();

    // when
    autosave.resync({ content: '같은 내용', revision: 6, maxLength: 20 });
    await settle();

    // then
    expect(autosave.getState().pendingDraft).toBeNull();
    expect(autosave.getState().content).toBe('같은 내용');
    expect(draftStore.read()).toBeNull();
  });

  it('진행 중인 저장이 있으면 끝난 뒤 서버 내용으로 맞춘다', async () => {
    // given
    const { autosave, save, calls } = setup();
    autosave.setContent('저장 중 내용');
    await vi.advanceTimersByTimeAsync(DELAY);
    expect(calls).toHaveLength(1);
    const waitForSave = autosave.beginResync();

    // when
    autosave.resync({ content: '다른 탭 내용', revision: 9, maxLength: 20 });
    await settle();

    // then 저장이 끝나기 전에는 반영하지 않는다
    expect(autosave.getState().content).toBe('저장 중 내용');
    expect(autosave.getState().loaded).toBe(false);

    // when
    calls[0].resolve();
    await waitForSave;
    await settle();

    // then 저장된 내용은 미저장이 아니므로 서버 내용으로 바뀐다
    const state = autosave.getState();
    expect(state.content).toBe('다른 탭 내용');
    expect(state.revision).toBe(9);
    expect(state.pendingDraft).toBeNull();
    expect(save).toHaveBeenCalledTimes(1);
  });
});
