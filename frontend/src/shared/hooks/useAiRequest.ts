import { useEffect, useRef, useState, useSyncExternalStore } from 'react';
import {
  createAiRequestController,
  type AiRequestCallbacks,
  type AiRequestControllerOptions,
} from '../api/aiRequestController';
import { AI_REQUEST_POLL_INTERVAL_MS } from '../constants/aiRequest';

export type UseAiRequestOptions<TInput, TResult> = Omit<
  AiRequestControllerOptions<TInput, TResult>,
  'pollIntervalMs'
> &
  AiRequestCallbacks<TInput, TResult> & {
    /**
     * 세션 회차. 바뀌면 이전 회차의 응답·조회 결과를 버리고 처음 상태로 돌아간다
     * (useGameSession의 reconnectKey를 넘긴다). 처음 값에서는 아무것도 하지 않는다.
     */
    runKey?: number;
    pollIntervalMs?: number;
  };

/**
 * AI 요청(NPC 질문·힌트·보충 질문·지목) 하나의 전송·처리 중 확인·재시도를 맡는 공통 훅.
 * 도메인에 묶이지 않도록 전송·상태 조회 함수를 받아서 쓴다. 상태 전이는 aiRequestController.ts에 있다.
 *
 * - 새 전송은 새 requestId, 재시도는 같은 requestId·입력
 * - 202이거나 응답 없이 연결이 끊기면 2초마다 상태를 조회하고, 완료·실패·언마운트·회차 변경 때 멈춘다
 * - 창을 닫아(언마운트) 확인이 멈춰도 서버의 요청은 취소되지 않는다. 다시 열 때 restore로 이어 간다
 */
export function useAiRequest<TInput, TResult>({
  runKey = 0,
  pollIntervalMs = AI_REQUEST_POLL_INTERVAL_MS,
  onCompleted,
  onRejected,
  onSessionRejected,
  ...controllerOptions
}: UseAiRequestOptions<TInput, TResult>) {
  // 화면이 살아 있는 동안 하나만 만든다. 전송 함수 등 바뀌는 값은 아래 effect가 매번 최신으로 넣는다
  const [controller] = useState(() =>
    createAiRequestController<TInput, TResult>({ ...controllerOptions, pollIntervalMs }),
  );

  useEffect(() => {
    controller.setOptions({ ...controllerOptions, pollIntervalMs });
    controller.setCallbacks({ onCompleted, onRejected, onSessionRejected });
  });

  useEffect(() => {
    controller.activate();
    return () => controller.deactivate();
  }, [controller]);

  const handledRunKeyRef = useRef(runKey);
  useEffect(() => {
    if (handledRunKeyRef.current === runKey) {
      return;
    }
    handledRunKeyRef.current = runKey;
    controller.reset();
  }, [controller, runKey]);

  const state = useSyncExternalStore(controller.subscribe, controller.getState);

  return {
    state,
    /** 전송 중이거나 결과를 기다리는 중. 이때는 새로 보낼 수 없다 */
    isBusy: state.phase === 'sending' || state.phase === 'processing',
    submit: controller.submit,
    retry: controller.retry,
    restore: controller.restore,
  };
}
