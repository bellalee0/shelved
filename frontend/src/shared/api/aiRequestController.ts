import { isApiError } from './apiError';
import type { AiPostResult } from './aiRequests';
import type { AiRequestStatusResponse } from './types';

// AI 요청 한 줄기(전송 → 처리 중 → 완료/실패 → 재시도)의 상태 전이. React와 무관한 순수 로직이며
// useAiRequest 훅이 화면과 연결한다 (노트의 autosave.ts와 같은 방식). 규칙: frontend.md "AI 요청의 requestId 처리".
//
// 상태 전이
//   idle ──submit──▶ sending ──200──▶ idle (onCompleted)
//                       ├──202·응답 없음──▶ processing ──(2초마다 상태 조회)──▶ idle / failed
//                       ├──5xx──▶ failed ──retry(같은 requestId·입력)──▶ sending
//                       └──4xx(차단·검증·세션 오류)──▶ idle + error (onRejected, 재시도 대상 아님)

export type AiRequestPhase = 'idle' | 'sending' | 'processing' | 'failed';

export type AiRequestError = {
  /** 백엔드 ErrorCode. 응답 없이 실패했으면 null */
  code: string | null;
  /** 화면에 그대로 보여 줄 문구 (백엔드 message 우선) */
  message: string;
};

export type AiRequestState<TInput> = {
  phase: AiRequestPhase;
  /** 진행 중이거나 마지막으로 실패한 요청의 ID. idle이면 null */
  requestId: string | null;
  /** 진행 중이거나 마지막으로 실패한 요청의 입력. 처리 중인 다른 요청으로 전환한 직후에는 null일 수 있다 */
  input: TInput | null;
  /** failed의 실패 사유, 또는 idle에서 마지막 전송이 거부된 사유 */
  error: AiRequestError | null;
};

export type AiRequestControllerOptions<TInput, TResult> = {
  /** AI POST. 같은 requestId·입력이면 서버가 중복 실행하지 않는다 */
  send: (requestId: string, input: TInput) => Promise<AiPostResult<TResult>>;
  /** GET ai-requests/{requestId} */
  getStatus: (requestId: string) => Promise<AiRequestStatusResponse>;
  /** 상태 조회의 result(unknown)를 완료 결과로 확인한다. 형식이 다르면 null */
  readResult: (result: unknown) => TResult | null;
  /** 상태 객체의 input에서 재시도에 쓸 입력을 꺼낸다 (복원·다른 요청으로 전환할 때). 형식이 다르면 null */
  readInput: (status: AiRequestStatusResponse) => TInput | null;
  /** 같은 입력인지. 실패한 요청과 같은 입력을 다시 보내면 같은 requestId를 쓴다. 기본값은 JSON 비교 */
  isSameInput?: (a: TInput, b: TInput) => boolean;
  /** 테스트에서 바꿀 수 있게 주입한다 */
  createRequestId?: () => string;
  pollIntervalMs: number;
};

export type AiRequestCallbacks<TInput, TResult> = {
  /** 완료. result는 즉시 표시용이며(최초 성공 당시 스냅샷), 현재 상태는 호출한 쪽이 다시 조회한다. 형식이 다르면 null */
  onCompleted?: (result: TResult | null) => void;
  /** 서버가 요청을 접수하지 않았다 (차단·검증 오류·세션 오류 등). 입력창 복원에 쓴다 */
  onRejected?: (input: TInput, error: AiRequestError) => void;
  /** 세션 상태 때문에 거부됐다. 게임 화면의 기존 흐름(상태 재조회·연결 교체 안내)이 처리한다 */
  onSessionRejected?: (code: string) => void;
};

/** 기존 세션 흐름(useGameSession.handleSessionRejected)에 맡기는 오류 코드 */
const SESSION_REJECTION_CODES = new Set([
  'CONNECTION_ID_REQUIRED',
  'ROOM_NOT_IN_PROGRESS',
  'ROOM_TIME_EXPIRED',
  'CONNECTION_REPLACED',
  'ROOM_ENDED_DURING_REQUEST',
]);

/** 상태 조회가 404 AI_REQUEST_NOT_FOUND일 때. 서버가 요청을 받지 못했으므로 다시 보내면 된다 */
export const AI_REQUEST_NOT_RECEIVED_MESSAGE =
  '요청이 서버에 전달되지 않았습니다. 같은 내용을 다시 보내 주세요.';

/** 실패 상태인데 서버가 error를 주지 않았을 때 */
const DEFAULT_FAILED_MESSAGE = '요청을 처리하지 못했습니다. 같은 내용을 다시 보내 주세요.';

export const IDLE_AI_REQUEST_STATE: AiRequestState<never> = {
  phase: 'idle',
  requestId: null,
  input: null,
  error: null,
};

function defaultIsSameInput<TInput>(a: TInput, b: TInput) {
  return JSON.stringify(a) === JSON.stringify(b);
}

function readDetailRequestId(detail: unknown): string | null {
  if (typeof detail === 'object' && detail !== null && 'requestId' in detail) {
    const value = (detail as { requestId: unknown }).requestId;
    return typeof value === 'string' ? value : null;
  }
  return null;
}

export function createAiRequestController<TInput, TResult>(
  initialOptions: AiRequestControllerOptions<TInput, TResult>,
) {
  let options = initialOptions;
  let callbacks: AiRequestCallbacks<TInput, TResult> = {};
  let state: AiRequestState<TInput> = IDLE_AI_REQUEST_STATE;
  const listeners = new Set<() => void>();
  // 지금 유효한 작업의 표식. 새 작업·정지·초기화 때 바꾸며, 표식이 다른 응답은 늦은 응답으로 보고 버린다
  let token: object | null = null;
  let timer: ReturnType<typeof setTimeout> | null = null;
  // 이 화면에서 완료를 확인한 requestId. 오래된 대화 기록이 같은 요청을 다시 "처리 중"으로 복원하지 않게 한다
  const finishedIds = new Set<string>();

  function setState(next: AiRequestState<TInput>) {
    state = next;
    listeners.forEach((listener) => listener());
  }

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  /** 진행 중인 응답 대기·조회 반복을 버린다. 상태는 그대로 둔다 */
  function stop() {
    token = null;
    clearTimer();
  }

  function begin(): object {
    stop();
    const next = {};
    token = next;
    return next;
  }

  function isCurrent(target: object) {
    return token === target;
  }

  function complete(requestId: string, result: TResult | null) {
    finishedIds.add(requestId);
    stop();
    setState(IDLE_AI_REQUEST_STATE);
    callbacks.onCompleted?.(result);
  }

  /** 재시도할 수 있는 실패. 입력을 모르면 재시도할 수 없으므로 안내만 남긴다 */
  function fail(requestId: string, input: TInput | null, error: AiRequestError) {
    stop();
    setState(
      input === null
        ? { ...IDLE_AI_REQUEST_STATE, error }
        : { phase: 'failed', requestId, input, error },
    );
  }

  /** 접수되지 않은 요청. 재시도 대상이 아니며 입력은 호출한 쪽이 입력창에 되돌린다 */
  function reject(input: TInput | null, error: AiRequestError) {
    stop();
    setState({ ...IDLE_AI_REQUEST_STATE, error });
    if (input !== null) {
      callbacks.onRejected?.(input, error);
    }
    if (error.code !== null && SESSION_REJECTION_CODES.has(error.code)) {
      callbacks.onSessionRejected?.(error.code);
    }
  }

  function startPolling(current: object, requestId: string, input: TInput | null) {
    setState({ phase: 'processing', requestId, input, error: null });
    schedulePoll(current, requestId, input);
  }

  function schedulePoll(current: object, requestId: string, input: TInput | null) {
    clearTimer();
    timer = setTimeout(() => {
      timer = null;
      void poll(current, requestId, input);
    }, options.pollIntervalMs);
  }

  async function poll(current: object, requestId: string, input: TInput | null) {
    let status: AiRequestStatusResponse;
    try {
      status = await options.getStatus(requestId);
    } catch (error) {
      if (!isCurrent(current)) {
        return;
      }
      if (isApiError(error) && error.code === 'AI_REQUEST_NOT_FOUND') {
        // 서버가 요청을 받지 못했다 (전송 중 연결이 끊김). 같은 ID·입력으로 다시 보내면 새로 접수된다
        fail(requestId, input, { code: error.code, message: AI_REQUEST_NOT_RECEIVED_MESSAGE });
        return;
      }
      // 네트워크 오류·5xx 같은 조회 자체의 일시 오류는 계속 확인한다
      schedulePoll(current, requestId, input);
      return;
    }
    if (isCurrent(current)) {
      applyStatus(current, status, input);
    }
  }

  function applyStatus(
    current: object,
    status: AiRequestStatusResponse,
    knownInput: TInput | null,
  ) {
    const input = knownInput ?? options.readInput(status);
    if (status.status === 'SUCCEEDED') {
      complete(status.requestId, options.readResult(status.result));
      return;
    }
    if (status.status === 'FAILED') {
      const error = status.error ?? { code: null, message: DEFAULT_FAILED_MESSAGE };
      if (error.code !== null && SESSION_REJECTION_CODES.has(error.code)) {
        // 처리 중 세션이 끝난 경우 등. 다시 보낼 수 없으므로 세션 흐름에 맡긴다
        stop();
        setState({ ...IDLE_AI_REQUEST_STATE, error });
        callbacks.onSessionRejected?.(error.code);
        return;
      }
      fail(status.requestId, input, error);
      return;
    }
    startPolling(current, status.requestId, input);
  }

  function handleSendError(current: object, requestId: string, input: TInput, error: unknown) {
    if (!isApiError(error)) {
      // 응답 형식 오류 등. 서버에 접수됐는지 알 수 없으므로 같은 ID로 다시 보낼 수 있게 한다
      fail(requestId, input, {
        code: null,
        message: error instanceof Error ? error.message : DEFAULT_FAILED_MESSAGE,
      });
      return;
    }
    if (error.isNetworkError) {
      // 응답 없이 연결이 끊겼다. 서버는 접수했을 수 있으므로 상태 조회로 결과를 확인한다
      startPolling(current, requestId, input);
      return;
    }
    const apiError: AiRequestError = { code: error.code, message: error.message };
    if (error.code === 'AI_REQUEST_IN_PROGRESS') {
      const processingId = readDetailRequestId(error.detail);
      if (processingId === requestId) {
        startPolling(current, requestId, input);
        return;
      }
      if (processingId !== null) {
        // 대상에 다른 요청이 처리 중이다 (예: 다른 탭에서 보낸 질문). 이번 입력은 되돌리고 그 요청을 기다린다
        callbacks.onRejected?.(input, apiError);
        startPolling(current, processingId, null);
        return;
      }
      reject(input, apiError);
      return;
    }
    if (error.status !== null && error.status >= 500) {
      // AI 실패(502 NPC_ANSWER_FAILED 등). 기회는 차감되지 않았고 같은 requestId·입력으로 다시 보낸다
      fail(requestId, input, apiError);
      return;
    }
    // QUESTION_BLOCKED·INVALID_INPUT·AI_REQUEST_ID_REUSED·세션 오류 등: 접수되지 않았다
    reject(input, apiError);
  }

  async function run(requestId: string, input: TInput) {
    const current = begin();
    setState({ phase: 'sending', requestId, input, error: null });
    let outcome: AiPostResult<TResult>;
    try {
      outcome = await options.send(requestId, input);
    } catch (error) {
      if (isCurrent(current)) {
        handleSendError(current, requestId, input, error);
      }
      return;
    }
    if (!isCurrent(current)) {
      return;
    }
    if (outcome.kind === 'completed') {
      complete(requestId, outcome.data);
    } else {
      applyStatus(current, outcome.data, input);
    }
  }

  function isBusy() {
    return state.phase === 'sending' || state.phase === 'processing';
  }

  return {
    getState: () => state,
    subscribe(listener: () => void) {
      listeners.add(listener);
      return () => {
        listeners.delete(listener);
      };
    },
    setOptions(next: AiRequestControllerOptions<TInput, TResult>) {
      options = next;
    },
    setCallbacks(next: AiRequestCallbacks<TInput, TResult>) {
      callbacks = next;
    },

    /**
     * 새로 보낸다. 처리 중이면 보내지 않고 false를 돌려준다 (상태 갱신 전 연속 클릭도 여기서 막힌다).
     * 마지막 실패한 요청과 같은 입력이면 그 requestId를 다시 쓰고, 입력이 다르면 새 requestId를 만든다.
     */
    submit(input: TInput): boolean {
      if (isBusy()) {
        return false;
      }
      const isSameInput = options.isSameInput ?? defaultIsSameInput;
      const reuse =
        state.phase === 'failed' &&
        state.requestId !== null &&
        state.input !== null &&
        isSameInput(state.input, input);
      const requestId = reuse ? state.requestId! : (options.createRequestId ?? randomId)();
      void run(requestId, input);
      return true;
    },

    /** 마지막 실패한 요청을 같은 requestId·입력으로 다시 보낸다 */
    retry(): boolean {
      if (state.phase !== 'failed' || state.requestId === null || state.input === null) {
        return false;
      }
      void run(state.requestId, state.input);
      return true;
    },

    /**
     * 서버가 알려 준 처리 중·실패 요청을 화면에 다시 표시한다 (대화창을 다시 열 때·재접속 때).
     * 이 화면에서 이미 다루고 있는 요청이 있거나, 완료를 확인한 요청이면 무시한다.
     */
    restore(status: AiRequestStatusResponse) {
      if (state.phase !== 'idle' || finishedIds.has(status.requestId)) {
        return;
      }
      if (status.status === 'SUCCEEDED') {
        return;
      }
      applyStatus(begin(), status, null);
    },

    /** 화면이 다시 보이기 시작할 때 (마운트). 정지되며 끊긴 확인을 이어 간다 */
    activate() {
      if (token !== null || (state.phase !== 'sending' && state.phase !== 'processing')) {
        return;
      }
      // 보내던 중 정지됐으면 서버가 받았는지 알 수 없으므로 상태 조회로 확인한다
      const { requestId, input } = state;
      if (requestId !== null) {
        startPolling(begin(), requestId, input);
      }
    },

    /** 화면이 사라질 때 (언마운트). 이후 도착하는 응답·조회 결과는 반영하지 않고 타이머를 남기지 않는다 */
    deactivate: stop,

    /** 세션 회차가 바뀌었을 때. 이전 회차의 응답을 모두 버리고 처음 상태로 돌아간다 */
    reset() {
      stop();
      finishedIds.clear();
      setState(IDLE_AI_REQUEST_STATE);
    },
  };
}

function randomId() {
  return crypto.randomUUID();
}

export type AiRequestController<TInput, TResult> = ReturnType<
  typeof createAiRequestController<TInput, TResult>
>;
