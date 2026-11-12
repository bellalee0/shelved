import { useQueryClient } from '@tanstack/react-query';
import { useEffect, useRef } from 'react';
import { getAiRequest } from '../../../shared/api/aiRequests';
import type { AiRequestError } from '../../../shared/api/aiRequestController';
import { useAiRequest } from '../../../shared/hooks/useAiRequest';
import { askNpc } from '../api';
import {
  mergeNpcMessages,
  pickPendingRequest,
  readNpcAskResult,
  readNpcQuestionInput,
} from '../messages';
import type { NpcAskResponse, NpcMessageListResponse, NpcQuestionInput } from '../types';
import { npcMessagesQueryKey, useNpcMessages } from './useNpcMessages';

export type UseNpcChatParams = {
  roomId: number;
  suspectId: number;
  /** 재접속할 때마다 바뀌는 값 (useGameSession의 reconnectKey). 바뀌면 대화 기록을 다시 받는다 */
  resyncKey: number;
  /** 새 답변이 완료됐을 때. 게임 화면이 GET state를 다시 조회해 대화 목록·시간을 맞춘다 */
  onAnswered?: () => void;
  /** 질문이 접수되지 않았을 때 (차단 등). 입력창에 질문을 되돌린다 */
  onRejected?: (input: NpcQuestionInput, error: AiRequestError) => void;
  onSessionRejected?: (code: string) => void;
};

/**
 * NPC 대화창의 데이터와 질문 전송을 묶는다.
 * - 대화 기록은 쿼리 캐시(useNpcMessages) 한 곳에서 관리한다
 * - 질문 전송·처리 중 확인·재시도는 공통 훅 useAiRequest가 맡는다
 * - 기록을 받을 때마다 pendingRequests로 처리 중·실패 요청을 복원한다 (창을 다시 열거나 재접속했을 때)
 * - 완료 결과는 즉시 표시용으로만 기록에 합치고(messageId로 중복 제거), 곧바로 GET messages를 다시 받는다
 */
export function useNpcChat({
  roomId,
  suspectId,
  resyncKey,
  onAnswered,
  onRejected,
  onSessionRejected,
}: UseNpcChatParams) {
  const queryClient = useQueryClient();
  const messagesQuery = useNpcMessages(roomId, suspectId);

  const request = useAiRequest<NpcQuestionInput, NpcAskResponse>({
    runKey: resyncKey,
    send: (requestId, input) => askNpc(roomId, suspectId, { requestId, question: input.question }),
    getStatus: (requestId) => getAiRequest(roomId, requestId),
    readResult: readNpcAskResult,
    readInput: readNpcQuestionInput,
    isSameInput: (a, b) => a.question === b.question,
    onCompleted: (result) => {
      const queryKey = npcMessagesQueryKey(roomId, suspectId);
      if (result) {
        queryClient.setQueryData<NpcMessageListResponse>(queryKey, (previous) =>
          previous
            ? {
                ...previous,
                messages: mergeNpcMessages(previous.messages, [result.question, result.answer]),
                pendingRequests: previous.pendingRequests.filter(
                  (pending) => pending.requestId !== result.requestId,
                ),
              }
            : previous,
        );
      }
      void queryClient.invalidateQueries({ queryKey });
      onAnswered?.();
    },
    onRejected,
    onSessionRejected,
  });

  const { restore } = request;
  const { data, refetch } = messagesQuery;

  useEffect(() => {
    const pending = data ? pickPendingRequest(data.pendingRequests) : null;
    if (pending) {
      restore(pending);
    }
  }, [data, restore]);

  // 재접속하면 기록을 다시 받는다. 내용이 같으면 data가 그대로라 위 effect가 돌지 않으므로 결과로 직접 복원한다
  const handledResyncKeyRef = useRef(resyncKey);
  useEffect(() => {
    if (handledResyncKeyRef.current === resyncKey) {
      return;
    }
    handledResyncKeyRef.current = resyncKey;
    void refetch().then((result) => {
      const pending = result.data ? pickPendingRequest(result.data.pendingRequests) : null;
      if (pending) {
        restore(pending);
      }
    });
  }, [resyncKey, refetch, restore]);

  return { messagesQuery, request };
}
