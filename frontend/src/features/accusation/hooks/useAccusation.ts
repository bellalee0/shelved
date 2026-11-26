import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef, useState } from 'react';
import { getAiRequest } from '../../../shared/api/aiRequests';
import type { AiRequestError } from '../../../shared/api/aiRequestController';
import type { AccusationResponse, AiRequestStatusResponse } from '../../../shared/api/types';
import { useAiRequest } from '../../../shared/hooks/useAiRequest';
import { createAccusation } from '../api';
import {
  isEndedRoomStatus,
  isGradingRetry,
  isSameAccusationInput,
  mergeAccusation,
  readAccusationInput,
  readAccusationResult,
  toRestorableAccusation,
} from '../accusations';
import type {
  AccusationCreateResponse,
  AccusationDraft,
  AccusationInput,
  AccusationListResponse,
} from '../types';
import { accusationsQueryKey } from './useAccusations';

export type UseAccusationParams = {
  roomId: number;
  /** GET state의 aiRequests·pendingAccusation. 바뀔 때마다 처리 중·실패 지목을 복원한다 */
  aiRequests: AiRequestStatusResponse[];
  pendingAccusation: AccusationResponse | null;
  /** 재접속할 때마다 바뀌는 값 (useGameSession의 reconnectKey) */
  reconnectKey: number;
  /** 채점이 끝났거나 횟수·세션 상태가 바뀌었을 수 있을 때. 게임 화면이 GET state를 다시 조회한다 */
  onStateChanged: () => void;
  /** 세션 상태 때문에 요청이 거부됐을 때 (연결 교체·시간 만료 등). 게임 화면의 기존 흐름이 처리한다 */
  onSessionRejected: (code: string) => void;
  /** 받은 결과의 roomStatus가 종료(CLEARED/FAILED/EXPIRED)일 때. GET state 확인보다 먼저 부른다 */
  onFinalResult?: () => void;
};

const EMPTY_DRAFT: AccusationDraft = { suspectId: null, trickExplanation: '' };

/** 서버가 "상황이 바뀌었다"고 알려 준 코드. 기록·횟수·세션 상태를 다시 받는다 */
const RESYNC_CODES = new Set([
  'AI_REQUEST_SUPERSEDED',
  'ACCUSATION_LIMIT_EXCEEDED',
  'AI_REQUEST_IN_PROGRESS',
  'AI_REQUEST_ID_REUSED',
  'SUSPECT_NOT_FOUND',
]);

/**
 * 범인 지목 한 줄기(입력 초안 → 제출 → 채점 중 → 결과/실패 → 재채점)를 게임 화면 단위로 맡는다.
 * 지목 창(AccusationPanel)이 아니라 게임 화면이 이 훅을 가지므로 창을 닫아도
 * - 입력 초안이 남고(메모리. 새로고침하면 사라진다)
 * - 채점 중 확인이 계속되며(서버 요청은 원래 취소되지 않는다)
 * - HUD가 처리 중·재채점 대기 표시를 할 수 있다.
 *
 * 전송·폴링·재시도는 공통 훅 useAiRequest를 쓴다. 새 답안은 연결 헤더를 붙이고, 채점 실패한 requestId를
 * 다시 보내는 재채점(sendRetry)은 헤더를 붙이지 않는다. 자동으로 반복 재시도하지 않는다.
 * 결과에 든 accusationCount·roomStatus(최초 성공 당시 스냅샷)로 현재 상태를 덮지 않고 GET state·GET accusations를 다시 받는다.
 */
export function useAccusation({
  roomId,
  aiRequests,
  pendingAccusation,
  reconnectKey,
  onStateChanged,
  onSessionRejected,
  onFinalResult,
}: UseAccusationParams) {
  const queryClient = useQueryClient();
  const [draft, setDraft] = useState<AccusationDraft>(EMPTY_DRAFT);
  // 방금 받은 채점 결과. 기록에서 강조하고 계속 수사·결과 보기 안내를 보여 준다. 새로 제출하면 지운다
  const [latestResult, setLatestResult] = useState<AccusationCreateResponse | null>(null);

  const refresh = useCallback(() => {
    void queryClient.invalidateQueries({ queryKey: accusationsQueryKey(roomId) });
    onStateChanged();
  }, [queryClient, roomId, onStateChanged]);

  const request = useAiRequest<AccusationInput, AccusationCreateResponse>({
    runKey: reconnectKey,
    send: (requestId, input) =>
      createAccusation(roomId, { requestId, ...input }, { isRetry: false }),
    sendRetry: (requestId, input, previousError: AiRequestError | null) =>
      createAccusation(roomId, { requestId, ...input }, { isRetry: isGradingRetry(previousError) }),
    getStatus: (requestId) => getAiRequest(roomId, requestId),
    readResult: readAccusationResult,
    readInput: readAccusationInput,
    isSameInput: isSameAccusationInput,
    onCompleted: (result) => {
      if (result) {
        setLatestResult(result);
        queryClient.setQueryData<AccusationListResponse>(accusationsQueryKey(roomId), (previous) =>
          previous
            ? { ...previous, accusations: mergeAccusation(previous.accusations, result.accusation) }
            : previous,
        );
        if (isEndedRoomStatus(result.roomStatus)) {
          onFinalResult?.();
        }
      }
      refresh();
    },
    onRejected: (_input, error) => {
      if (error.code !== null && RESYNC_CODES.has(error.code)) {
        refresh();
      }
    },
    onSessionRejected: (code) => {
      // 시간 만료·처리 중 종료 등. 기록은 여기서, 세션 상태는 게임 화면의 기존 흐름이 다시 받는다
      void queryClient.invalidateQueries({ queryKey: accusationsQueryKey(roomId) });
      onSessionRejected(code);
    },
  });

  const { restore, submit: submitRequest, retry: retryRequest } = request;
  const phase = request.state.phase;

  // 채점 실패를 확인하면 기록(ERROR 항목)과 세션 상태(처리 중 확보 해제)를 다시 받는다.
  // 실패로 바뀐 순간에만 한 번 받도록 refresh는 ref로 읽는다
  const refreshRef = useRef(refresh);
  useEffect(() => {
    refreshRef.current = refresh;
  });
  useEffect(() => {
    if (phase === 'failed') {
      refreshRef.current();
    }
  }, [phase]);

  // 서버가 알려 준 처리 중·실패 지목을 복원한다. 폴링·재채점 입력은 aiRequests 기준이다.
  // 이 화면이 이미 다루고 있는 요청이 있거나 완료를 확인한 요청이면 restore가 무시한다.
  // 재접속(reconnectKey 변경)하면 공통 훅이 처음 상태로 돌아가므로 다시 복원한다
  useEffect(() => {
    const status = toRestorableAccusation(aiRequests, pendingAccusation);
    if (status) {
      restore(status);
    }
  }, [aiRequests, pendingAccusation, reconnectKey, restore]);

  /** 새 답안을 제출한다. 처리 중이면 보내지 않고 false */
  const submit = useCallback(() => {
    if (draft.suspectId === null) {
      return false;
    }
    const sent = submitRequest({
      suspectId: draft.suspectId,
      trickExplanation: draft.trickExplanation.trim(),
    });
    if (sent) {
      setLatestResult(null);
    }
    return sent;
  }, [draft, submitRequest]);

  /** 채점에 실패한 답안을 같은 requestId·입력으로 다시 채점한다 (사용자가 누를 때만) */
  const retry = useCallback(() => {
    const sent = retryRequest();
    if (sent) {
      setLatestResult(null);
    }
    return sent;
  }, [retryRequest]);

  const failedInput = phase === 'failed' ? request.state.input : null;

  return {
    state: request.state,
    isBusy: request.isBusy,
    /** 채점 중이거나 재채점을 기다리는 지목이 있다 (HUD 점 표시) */
    hasActivity: request.isBusy || phase === 'failed',
    draft,
    setDraft,
    latestResult,
    /** 지금 입력이 채점 실패한 답안과 같은지 (같으면 새 답안이 아니라 재채점이다) */
    isSameAsFailedInput:
      failedInput !== null &&
      draft.suspectId !== null &&
      isSameAccusationInput(failedInput, {
        suspectId: draft.suspectId,
        trickExplanation: draft.trickExplanation.trim(),
      }),
    submit,
    retry,
  };
}

export type AccusationController = ReturnType<typeof useAccusation>;
