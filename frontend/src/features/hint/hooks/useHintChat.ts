import { useQueryClient } from '@tanstack/react-query';
import { useCallback, useEffect, useRef } from 'react';
import { getAiRequest } from '../../../shared/api/aiRequests';
import type { AiRequestError } from '../../../shared/api/aiRequestController';
import type { AiRequestStatusResponse } from '../../../shared/api/types';
import { useAiRequest } from '../../../shared/hooks/useAiRequest';
import { createHint, createHintFollowup } from '../api';
import {
  attachFollowup,
  mergeHint,
  pickPendingRequest,
  readFollowupQuestionInput,
  readFollowupResult,
  readHintQuestionInput,
  readHintResult,
  removePending,
} from '../hints';
import type {
  FollowupQuestionInput,
  HintCreateResponse,
  HintFollowupResponse,
  HintListResponse,
  HintQuestionInput,
} from '../types';
import { hintsQueryKey, useHints } from './useHints';

export type UseHintChatParams = {
  roomId: number;
  /** 재접속할 때마다 바뀌는 값 (useGameSession의 reconnectKey). 바뀌면 기록을 다시 받는다 */
  resyncKey: number;
  /** 요청이 끝났거나 횟수가 바뀌었을 가능성이 있을 때. 게임 화면이 GET state를 다시 조회한다 */
  onCountChanged?: () => void;
  /** 새 힌트가 접수되지 않았을 때 (차단 등). 입력창에 질문을 되돌린다 */
  onHintRejected?: (input: HintQuestionInput, error: AiRequestError) => void;
  onFollowupRejected?: (input: FollowupQuestionInput, error: AiRequestError) => void;
  onSessionRejected?: (code: string) => void;
};

/** 서버가 "이미 끝난 일"을 알려 준 코드. 기록·횟수를 다시 받아 화면을 맞춘다 */
const RESYNC_CODES = new Set(['HINT_LIMIT_EXCEEDED', 'HINT_FOLLOWUP_ALREADY_USED']);

/**
 * 힌트 대화창의 데이터와 요청 전송을 묶는다.
 * - 기록은 쿼리 캐시(useHints) 한 곳에서 관리한다
 * - 새 힌트·보충 질문 전송은 공통 훅 useAiRequest를 각각 하나씩 쓴다 (NPC 대화창과 같은 방식)
 * - 기록을 받을 때마다 pendingRequests로 처리 중·실패 요청을 복원한다 (창을 다시 열거나 재접속했을 때)
 * - 완료 결과는 즉시 표시용으로만 기록에 합치고(hintId로 중복 제거), 곧바로 GET hints·GET state를 다시 받는다.
 *   결과에 든 hintCount(최초 성공 당시 스냅샷)로는 현재 횟수를 덮지 않는다
 */
export function useHintChat({
  roomId,
  resyncKey,
  onCountChanged,
  onHintRejected,
  onFollowupRejected,
  onSessionRejected,
}: UseHintChatParams) {
  const queryClient = useQueryClient();
  const hintsQuery = useHints(roomId);
  const queryKey = hintsQueryKey(roomId);
  // 보충 질문이 어느 힌트의 것인지. 완료 결과를 그 힌트에 바로 붙이려고 요청이 시작될 때 기억해 둔다
  const followupTargetRef = useRef<number | null>(null);

  function refresh() {
    void queryClient.invalidateQueries({ queryKey });
    onCountChanged?.();
  }

  function handleRejected(error: AiRequestError) {
    if (error.code !== null && RESYNC_CODES.has(error.code)) {
      refresh();
    }
  }

  const hintRequest = useAiRequest<HintQuestionInput, HintCreateResponse>({
    runKey: resyncKey,
    send: (requestId, input) => createHint(roomId, { requestId, question: input.question }),
    getStatus: (requestId) => getAiRequest(roomId, requestId),
    readResult: readHintResult,
    readInput: readHintQuestionInput,
    isSameInput: (a, b) => a.question === b.question,
    onCompleted: (result) => {
      if (result) {
        queryClient.setQueryData<HintListResponse>(queryKey, (previous) =>
          previous
            ? {
                ...removePending(previous, result.hint.requestId),
                hints: mergeHint(previous.hints, result.hint),
              }
            : previous,
        );
      }
      refresh();
    },
    onRejected: (input, error) => {
      onHintRejected?.(input, error);
      handleRejected(error);
    },
    onSessionRejected,
  });

  const followupRequest = useAiRequest<FollowupQuestionInput, HintFollowupResponse>({
    runKey: resyncKey,
    send: (requestId, input) => {
      followupTargetRef.current = input.hintId;
      return createHintFollowup(roomId, input.hintId, { requestId, question: input.question });
    },
    getStatus: (requestId) => getAiRequest(roomId, requestId),
    readResult: readFollowupResult,
    readInput: readFollowupQuestionInput,
    isSameInput: (a, b) => a.hintId === b.hintId && a.question === b.question,
    onCompleted: (result) => {
      const hintId = followupTargetRef.current ?? result?.hintId ?? null;
      if (result && hintId !== null) {
        queryClient.setQueryData<HintListResponse>(queryKey, (previous) =>
          previous
            ? {
                ...removePending(previous, result.requestId),
                hints: attachFollowup(previous.hints, hintId, result),
              }
            : previous,
        );
      }
      followupTargetRef.current = null;
      refresh();
    },
    onRejected: (input, error) => {
      onFollowupRejected?.(input, error);
      handleRejected(error);
    },
    onSessionRejected,
  });

  const { restore: restoreHint } = hintRequest;
  const { restore: restoreFollowup } = followupRequest;
  // restoreAll이 렌더마다 바뀌지 않도록 현재 단계는 ref로 읽는다
  const followupPhaseRef = useRef(followupRequest.state.phase);
  useEffect(() => {
    followupPhaseRef.current = followupRequest.state.phase;
  });

  // 서버가 알려 준 처리 중·실패 요청을 유형별로 화면에 복원한다. 보충 질문은 어느 힌트의 것인지도 기억한다
  const restoreAll = useCallback(
    (pendingRequests: AiRequestStatusResponse[]) => {
      const hint = pickPendingRequest(pendingRequests, 'HINT');
      if (hint) {
        restoreHint(hint);
      }
      const followup = pickPendingRequest(pendingRequests, 'HINT_FOLLOWUP');
      if (followup) {
        if (followupPhaseRef.current === 'idle') {
          followupTargetRef.current = followup.targetId;
        }
        restoreFollowup(followup);
      }
    },
    [restoreHint, restoreFollowup],
  );

  const { data, refetch } = hintsQuery;

  useEffect(() => {
    if (data) {
      restoreAll(data.pendingRequests);
    }
  }, [data, restoreAll]);

  // 재접속하면 기록을 다시 받는다. 내용이 같으면 data가 그대로라 위 effect가 돌지 않으므로 결과로 직접 복원한다
  const handledResyncKeyRef = useRef(resyncKey);
  useEffect(() => {
    if (handledResyncKeyRef.current === resyncKey) {
      return;
    }
    handledResyncKeyRef.current = resyncKey;
    void refetch().then((result) => {
      if (result.data) {
        restoreAll(result.data.pendingRequests);
      }
    });
  }, [resyncKey, refetch, restoreAll]);

  return { hintsQuery, hintRequest, followupRequest };
}
