import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ApiError } from '../api/apiError';
import { AI_REQUEST_NOT_RECEIVED_MESSAGE } from '../api/aiRequestController';
import type { AiPostResult } from '../api/aiRequests';
import type { AiRequestStatusResponse } from '../api/types';
import { useAiRequest, type UseAiRequestOptions } from './useAiRequest';

type Input = { question: string };
type Result = { requestId: string; answer: string };

const POLL_MS = 2000;

function apiError(status: number | null, code: string | null, detail: unknown = null) {
  return new ApiError({
    status,
    message: `${code ?? 'NETWORK'} 안내`,
    code,
    detail,
    isNetworkError: status === null,
  });
}

function statusOf(
  requestId: string,
  status: AiRequestStatusResponse['status'],
  override: Partial<AiRequestStatusResponse> = {},
): AiRequestStatusResponse {
  return {
    requestId,
    type: 'NPC_ASK',
    targetId: 1,
    status,
    input: { question: '어젯밤 어디에 있었나요?' },
    result: status === 'SUCCEEDED' ? { requestId, answer: '정원에 있었습니다.' } : null,
    error:
      status === 'FAILED'
        ? { code: 'NPC_ANSWER_FAILED', message: 'NPC의 답변을 받지 못했습니다.' }
        : null,
    retryAvailableAt: null,
    acceptedAt: '2026-11-12T10:00:00',
    updatedAt: '2026-11-12T10:00:00',
    ...override,
  };
}

function completed(requestId: string): AiPostResult<Result> {
  return { kind: 'completed', data: { requestId, answer: '정원에 있었습니다.' } };
}

function deferred<T>() {
  let resolve!: (value: T) => void;
  let reject!: (reason: unknown) => void;
  const promise = new Promise<T>((res, rej) => {
    resolve = res;
    reject = rej;
  });
  return { promise, resolve, reject };
}

/** 훅을 띄우고, 호출을 확인할 가짜 함수들을 options로 돌려준다. override로 기본 옵션을 바꾼다 */
function setup(override: Partial<UseAiRequestOptions<Input, Result>> = {}) {
  let idCount = 0;
  const mocks = {
    send: vi.fn<(requestId: string, input: Input) => Promise<AiPostResult<Result>>>(),
    getStatus: vi.fn<(requestId: string) => Promise<AiRequestStatusResponse>>(),
    createRequestId: vi.fn(() => `request-${++idCount}`),
    onCompleted: vi.fn(),
    onRejected: vi.fn(),
    onSessionRejected: vi.fn(),
  };
  const hookOptions: UseAiRequestOptions<Input, Result> = {
    ...mocks,
    readResult: (result: unknown) => (result as Result | null) ?? null,
    readInput: (status: AiRequestStatusResponse) => status.input as Input,
    pollIntervalMs: POLL_MS,
    ...override,
  };
  const hook = renderHook(
    ({ runKey }: { runKey: number }) => useAiRequest<Input, Result>({ ...hookOptions, runKey }),
    { initialProps: { runKey: 0 } },
  );
  return { ...hook, options: mocks };
}

const QUESTION = { question: '어젯밤 어디에 있었나요?' };

async function advance(ms: number) {
  await act(async () => {
    await vi.advanceTimersByTimeAsync(ms);
  });
}

beforeEach(() => {
  vi.useFakeTimers();
});

afterEach(() => {
  vi.useRealTimers();
  vi.restoreAllMocks();
});

describe('useAiRequest 전송과 requestId', () => {
  it('새로 보내면 crypto.randomUUID로 새 requestId를 만든다', async () => {
    // given
    const uuid = '5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11';
    vi.spyOn(crypto, 'randomUUID').mockReturnValue(uuid);
    const { result, options } = setup({ createRequestId: undefined });
    options.send.mockResolvedValue(completed(uuid));

    // when
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // then
    expect(options.send).toHaveBeenCalledWith(uuid, QUESTION);
    expect(options.onCompleted).toHaveBeenCalledWith({
      requestId: uuid,
      answer: '정원에 있었습니다.',
    });
    expect(result.current.state.phase).toBe('idle');
  });

  it('502 실패 후 다시 보내기는 같은 requestId와 같은 입력을 보낸다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValueOnce(apiError(502, 'NPC_ANSWER_FAILED'));
    await act(async () => {
      result.current.submit(QUESTION);
    });
    expect(result.current.state).toMatchObject({
      phase: 'failed',
      requestId: 'request-1',
      error: { code: 'NPC_ANSWER_FAILED' },
    });

    // when
    options.send.mockResolvedValueOnce(completed('request-1'));
    await act(async () => {
      result.current.retry();
    });

    // then
    expect(options.send).toHaveBeenNthCalledWith(2, 'request-1', QUESTION);
    expect(options.createRequestId).toHaveBeenCalledTimes(1);
    expect(result.current.state.phase).toBe('idle');
  });

  it('실패 후 같은 입력을 다시 입력해 보내면 같은 requestId, 입력을 바꾸면 새 requestId를 쓴다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValue(apiError(502, 'NPC_ANSWER_FAILED'));
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // when 같은 입력
    await act(async () => {
      result.current.submit({ question: QUESTION.question });
    });

    // then
    expect(options.send).toHaveBeenLastCalledWith('request-1', QUESTION);

    // when 바꾼 입력
    await act(async () => {
      result.current.submit({ question: '그 시각에 누구를 봤나요?' });
    });

    // then
    expect(options.send).toHaveBeenLastCalledWith('request-2', {
      question: '그 시각에 누구를 봤나요?',
    });
  });

  it('처리 중에는 연속으로 눌러도 한 번만 보낸다', async () => {
    // given
    const { result, options } = setup();
    const response = deferred<AiPostResult<Result>>();
    options.send.mockReturnValue(response.promise);

    // when 상태가 화면에 반영되기 전의 같은 틱 두 번 클릭
    let accepted: boolean[] = [];
    act(() => {
      accepted = [result.current.submit(QUESTION), result.current.submit(QUESTION)];
    });

    // then
    expect(accepted).toEqual([true, false]);
    expect(options.send).toHaveBeenCalledTimes(1);
    expect(result.current.isBusy).toBe(true);
    await act(async () => response.resolve(completed('request-1')));
    expect(result.current.isBusy).toBe(false);
  });

  it('QUESTION_BLOCKED는 기록되지 않으므로 재시도 대상이 아니고, 입력을 고쳐 새 요청으로 보낸다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValueOnce(apiError(400, 'QUESTION_BLOCKED'));

    // when
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // then 입력은 호출한 쪽에 돌려주고, 안내만 남는다
    expect(result.current.state).toMatchObject({
      phase: 'idle',
      requestId: null,
      error: { code: 'QUESTION_BLOCKED', message: 'QUESTION_BLOCKED 안내' },
    });
    expect(options.onRejected).toHaveBeenCalledWith(QUESTION, {
      code: 'QUESTION_BLOCKED',
      message: 'QUESTION_BLOCKED 안내',
    });
    expect(result.current.retry()).toBe(false);

    // when 고친 질문을 새로 보낸다
    const edited = { question: '어젯밤 정원에서 무엇을 봤나요?' };
    options.send.mockResolvedValueOnce(completed('request-2'));
    await act(async () => {
      result.current.submit(edited);
    });

    // then 차단된 요청은 접수되지 않았으므로 새 요청으로 보내도 충돌하지 않는다
    expect(options.send).toHaveBeenLastCalledWith('request-2', edited);
    expect(options.onCompleted).toHaveBeenCalledTimes(1);
    expect(result.current.state).toMatchObject({ phase: 'idle', error: null });
  });

  it('세션 상태 오류는 기존 세션 흐름에 넘긴다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValueOnce(apiError(409, 'CONNECTION_REPLACED'));

    // when
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // then
    expect(options.onSessionRejected).toHaveBeenCalledWith('CONNECTION_REPLACED');
    expect(options.onRejected).toHaveBeenCalledWith(QUESTION, expect.anything());
    expect(result.current.state.phase).toBe('idle');
  });
});

describe('useAiRequest 처리 중 상태 조회', () => {
  it('202면 2초 간격으로 상태를 조회하고, 완료되면 결과를 넘기고 조회를 멈춘다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockResolvedValue({
      kind: 'processing',
      data: statusOf('request-1', 'PROCESSING'),
    });
    options.getStatus
      .mockResolvedValueOnce(statusOf('request-1', 'PROCESSING'))
      .mockResolvedValueOnce(statusOf('request-1', 'SUCCEEDED'));

    // when
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // then
    expect(result.current.state).toMatchObject({ phase: 'processing', requestId: 'request-1' });
    expect(options.getStatus).not.toHaveBeenCalled();

    // when
    await advance(POLL_MS);
    // then
    expect(options.getStatus).toHaveBeenCalledTimes(1);
    expect(result.current.state.phase).toBe('processing');

    // when
    await advance(POLL_MS);
    // then
    expect(options.getStatus).toHaveBeenCalledTimes(2);
    expect(options.onCompleted).toHaveBeenCalledWith({
      requestId: 'request-1',
      answer: '정원에 있었습니다.',
    });
    expect(result.current.state.phase).toBe('idle');
    expect(vi.getTimerCount()).toBe(0);

    await advance(POLL_MS * 3);
    expect(options.getStatus).toHaveBeenCalledTimes(2);
  });

  it('조회 중 실패로 바뀌면 다시 보낼 수 있는 실패 상태가 되고 조회를 멈춘다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockResolvedValue({
      kind: 'processing',
      data: statusOf('request-1', 'PROCESSING'),
    });
    options.getStatus.mockResolvedValue(statusOf('request-1', 'FAILED'));
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // when
    await advance(POLL_MS);

    // then
    expect(result.current.state).toMatchObject({
      phase: 'failed',
      requestId: 'request-1',
      input: QUESTION,
      error: { code: 'NPC_ANSWER_FAILED' },
    });
    expect(vi.getTimerCount()).toBe(0);
  });

  it('응답 없이 연결이 끊기면 상태 조회로 결과를 확인한다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValue(apiError(null, null));
    options.getStatus.mockResolvedValue(statusOf('request-1', 'SUCCEEDED'));

    // when
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // then
    expect(result.current.state.phase).toBe('processing');

    // when
    await advance(POLL_MS);

    // then
    expect(options.getStatus).toHaveBeenCalledWith('request-1');
    expect(options.onCompleted).toHaveBeenCalledTimes(1);
    expect(result.current.state.phase).toBe('idle');
  });

  it('조회 자체의 일시 오류는 계속 시도하고, 404 AI_REQUEST_NOT_FOUND면 멈추고 다시 보내기를 허용한다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValueOnce(apiError(null, null));
    options.getStatus
      .mockRejectedValueOnce(apiError(null, null))
      .mockRejectedValueOnce(apiError(500, 'INTERNAL_SERVER_ERROR'))
      .mockRejectedValueOnce(apiError(404, 'AI_REQUEST_NOT_FOUND'));
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // when
    await advance(POLL_MS * 3);

    // then
    expect(options.getStatus).toHaveBeenCalledTimes(3);
    expect(result.current.state).toMatchObject({
      phase: 'failed',
      requestId: 'request-1',
      error: { message: AI_REQUEST_NOT_RECEIVED_MESSAGE },
    });
    expect(vi.getTimerCount()).toBe(0);

    // when 다시 보내기는 같은 requestId·입력
    options.send.mockResolvedValueOnce(completed('request-1'));
    await act(async () => {
      result.current.retry();
    });

    // then
    expect(options.send).toHaveBeenLastCalledWith('request-1', QUESTION);
  });

  it('AI_REQUEST_IN_PROGRESS면 입력을 돌려주고 처리 중인 요청의 상태 조회로 전환한다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockRejectedValue(
      apiError(409, 'AI_REQUEST_IN_PROGRESS', { requestId: 'other-request' }),
    );
    options.getStatus.mockResolvedValue(
      statusOf('other-request', 'PROCESSING', { input: { question: '다른 탭의 질문' } }),
    );

    // when
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // then
    expect(options.onRejected).toHaveBeenCalledWith(QUESTION, expect.anything());
    expect(result.current.state).toMatchObject({
      phase: 'processing',
      requestId: 'other-request',
      input: null,
    });

    // when
    await advance(POLL_MS);

    // then 조회 결과의 입력으로 채운다
    expect(options.getStatus).toHaveBeenCalledWith('other-request');
    expect(result.current.state.input).toEqual({ question: '다른 탭의 질문' });
  });
});

describe('useAiRequest 늦은 응답과 타이머 정리', () => {
  it('언마운트 뒤 도착한 응답은 반영하지 않는다', async () => {
    // given
    const { result, options, unmount } = setup();
    const response = deferred<AiPostResult<Result>>();
    options.send.mockReturnValue(response.promise);
    act(() => {
      result.current.submit(QUESTION);
    });

    // when
    unmount();
    await act(async () => response.resolve(completed('request-1')));

    // then
    expect(options.onCompleted).not.toHaveBeenCalled();
  });

  it('언마운트하면 상태 조회를 멈추고 타이머를 남기지 않는다', async () => {
    // given
    const { result, options, unmount } = setup();
    options.send.mockResolvedValue({
      kind: 'processing',
      data: statusOf('request-1', 'PROCESSING'),
    });
    await act(async () => {
      result.current.submit(QUESTION);
    });
    expect(vi.getTimerCount()).toBe(1);

    // when
    unmount();
    await advance(POLL_MS * 3);

    // then
    expect(vi.getTimerCount()).toBe(0);
    expect(options.getStatus).not.toHaveBeenCalled();
  });

  it('조회 응답을 기다리는 중 언마운트하면 그 결과도 버린다', async () => {
    // given
    const { result, options, unmount } = setup();
    const status = deferred<AiRequestStatusResponse>();
    options.send.mockResolvedValue({
      kind: 'processing',
      data: statusOf('request-1', 'PROCESSING'),
    });
    options.getStatus.mockReturnValue(status.promise);
    await act(async () => {
      result.current.submit(QUESTION);
    });
    await advance(POLL_MS);

    // when
    unmount();
    await act(async () => status.resolve(statusOf('request-1', 'SUCCEEDED')));

    // then
    expect(options.onCompleted).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });

  it('여러 번 마운트·언마운트해도 타이머가 남지 않는다', async () => {
    for (let index = 0; index < 3; index += 1) {
      // given
      const { result, options, unmount } = setup();
      options.send.mockResolvedValue({
        kind: 'processing',
        data: statusOf('request-1', 'PROCESSING'),
      });
      await act(async () => {
        result.current.submit(QUESTION);
      });

      // when
      unmount();
    }

    // then
    expect(vi.getTimerCount()).toBe(0);
  });

  it('세션 회차가 바뀐 뒤 도착한 응답은 반영하지 않고 처음 상태로 돌아간다', async () => {
    // given
    const { result, options, rerender } = setup();
    const response = deferred<AiPostResult<Result>>();
    options.send.mockReturnValue(response.promise);
    act(() => {
      result.current.submit(QUESTION);
    });

    // when
    rerender({ runKey: 1 });
    await act(async () => response.resolve(completed('request-1')));

    // then
    expect(options.onCompleted).not.toHaveBeenCalled();
    expect(result.current.state.phase).toBe('idle');
    expect(result.current.isBusy).toBe(false);
  });

  it('세션 회차가 바뀌면 상태 조회도 멈춘다', async () => {
    // given
    const { result, options, rerender } = setup();
    options.send.mockResolvedValue({
      kind: 'processing',
      data: statusOf('request-1', 'PROCESSING'),
    });
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // when
    rerender({ runKey: 1 });
    await advance(POLL_MS * 2);

    // then
    expect(options.getStatus).not.toHaveBeenCalled();
    expect(vi.getTimerCount()).toBe(0);
  });
});

describe('useAiRequest 복원', () => {
  it('서버가 알려 준 처리 중 요청을 복원하면 상태 조회를 이어 간다', async () => {
    // given
    const { result, options } = setup();
    options.getStatus.mockResolvedValue(statusOf('saved-request', 'SUCCEEDED'));

    // when
    act(() => result.current.restore(statusOf('saved-request', 'PROCESSING')));

    // then
    expect(result.current.state).toMatchObject({
      phase: 'processing',
      requestId: 'saved-request',
      input: QUESTION,
    });

    // when
    await advance(POLL_MS);

    // then
    expect(options.onCompleted).toHaveBeenCalledTimes(1);
  });

  it('서버가 알려 준 실패 요청을 복원하면 같은 requestId·입력으로 다시 보낼 수 있다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockResolvedValue(completed('saved-request'));
    act(() => result.current.restore(statusOf('saved-request', 'FAILED')));

    // when
    await act(async () => {
      result.current.retry();
    });

    // then
    expect(options.send).toHaveBeenCalledWith('saved-request', QUESTION);
    expect(options.createRequestId).not.toHaveBeenCalled();
  });

  it('이 화면에서 완료를 확인한 요청은 오래된 기록으로 다시 복원하지 않는다', async () => {
    // given
    const { result, options } = setup();
    options.send.mockResolvedValue(completed('request-1'));
    await act(async () => {
      result.current.submit(QUESTION);
    });

    // when 완료 전에 받아 둔 기록에 처리 중으로 남아 있던 같은 요청
    act(() => result.current.restore(statusOf('request-1', 'PROCESSING')));

    // then
    expect(result.current.state.phase).toBe('idle');
    expect(vi.getTimerCount()).toBe(0);
  });
});
