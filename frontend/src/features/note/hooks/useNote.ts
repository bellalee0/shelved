import { useCallback, useEffect, useMemo, useRef, useSyncExternalStore } from 'react';
import { useQuery } from '@tanstack/react-query';
import { NOTE_AUTOSAVE_DELAY_MS } from '../../../shared/constants/note';
import { getMyNote, updateMyNote } from '../api';
import { createAutosave, type NoteFlushResult, type NoteState } from '../autosave';
import { createDraftStore } from '../draft';

export type UseNoteParams = {
  roomId: number;
  userId: number;
  /** 지금 노트를 저장할 수 있는지. 게임 화면이 서버 상태로 계산한다 (IN_PROGRESS·유효한 연결·남은 시간 > 0) */
  operable: boolean;
  /** 저장이 세션 상태 때문에 거부됐을 때 (409 ROOM_NOT_IN_PROGRESS·ROOM_TIME_EXPIRED·CONNECTION_REPLACED, 400 CONNECTION_ID_REQUIRED) */
  onSessionRejected?: (code: string) => void;
  /**
   * 이 화면이 연결을 잃었다가 다시 연결할 때마다 바뀌는 값 (useGameSession의 reconnectKey).
   * 바뀌면 서버 노트를 다시 받아 맞춘다. 처음 값에서는 다시 받지 않는다(첫 조회와 중복 방지)
   */
  resyncKey?: number;
};

/** 게임 화면이 떠 있는 동안 계속 살아 있는 노트 상태. 노트 창이 닫혀 있어도 flush가 동작한다 */
export type NoteHandle = {
  /** 진행 중인 저장이 끝나길 기다린 뒤, 저장 안 된 내용이 있으면 저장한다. 저장할 것이 없으면 NO_CHANGES */
  flush: () => Promise<NoteFlushResult>;
  /** 이 기기에 서버에 저장되지 않은 초안이 있는지 (PAUSED 복원 화면 안내용) */
  hasUnsavedDraft: boolean;
  // 아래는 NoteWindow가 쓰는 내부 값
  loadStatus: 'loading' | 'error' | 'ready';
  loadErrorMessage: string | null;
  reload: () => void;
  state: NoteState;
  setContent: (content: string) => void;
  retry: () => void;
  resolveWithServer: () => void;
  overwriteWithMine: () => void;
  continueWithDraft: () => void;
  discardDraft: () => void;
};

/**
 * 개인 노트의 조회·자동 저장·로컬 초안·충돌 상태를 맡는 훅. 게임 화면(페이지)에서 한 번만 호출한다.
 * 저장 로직 자체는 autosave.ts에 있고, 이 훅은 React와 연결만 한다.
 */
export function useNote({
  roomId,
  userId,
  operable,
  onSessionRejected,
  resyncKey = 0,
}: UseNoteParams): NoteHandle {
  const controller = useMemo(
    () =>
      createAutosave({
        save: (content, revision) => updateMyNote(roomId, { content, revision }),
        delayMs: NOTE_AUTOSAVE_DELAY_MS,
        draftStore: createDraftStore(userId, roomId),
      }),
    [roomId, userId],
  );

  // 처음 한 번만 불러온다. 게임 중에는 다시 불러오지 않고(덮어쓰기 방지), 화면을 다시 열거나
  // 재접속(resyncKey 변경)할 때만 다시 불러온다
  const query = useQuery({
    queryKey: ['note', userId, roomId],
    queryFn: () => getMyNote(roomId),
    staleTime: Infinity,
    gcTime: 0,
    retry: false,
    refetchOnWindowFocus: false,
    refetchOnReconnect: false,
  });

  useEffect(() => {
    if (query.data) {
      controller.init(query.data);
    }
  }, [controller, query.data]);

  // 재접속 때 서버 노트를 다시 받아 맞춘다. 늦게 끝난 이전 회차 조회는 버린다
  const refetch = query.refetch;
  const resyncRunRef = useRef(0);
  const resync = useCallback(async () => {
    const run = ++resyncRunRef.current;
    // 진행 중인 저장이 끝난 뒤에 조회해야 방금 저장한 내용보다 오래된 노트를 받지 않는다
    await controller.beginResync();
    if (run !== resyncRunRef.current) {
      return;
    }
    // 같은 내용이면 query.data가 그대로라 effect로는 알 수 없으므로 결과를 직접 넘긴다
    const result = await refetch();
    if (run === resyncRunRef.current && result.isSuccess) {
      controller.resync(result.data);
    }
  }, [controller, refetch]);

  const handledResyncKeyRef = useRef(resyncKey);
  useEffect(() => {
    if (handledResyncKeyRef.current === resyncKey) {
      return;
    }
    handledResyncKeyRef.current = resyncKey;
    void resync();
  }, [resyncKey, resync]);

  useEffect(() => {
    controller.setOnSessionRejected(onSessionRejected);
  }, [controller, onSessionRejected]);

  useEffect(() => {
    controller.setOperable(operable);
  }, [controller, operable]);

  useEffect(() => () => controller.dispose(), [controller]);

  const state = useSyncExternalStore(controller.subscribe, controller.getState);

  // 다시 받기를 시작한 뒤의 조회 실패는 같은 다시 받기 흐름으로 재시도한다
  const reload = useCallback(() => {
    void (resyncRunRef.current > 0 ? resync() : refetch());
  }, [resync, refetch]);

  return {
    flush: controller.flush,
    hasUnsavedDraft: state.dirty || state.pendingDraft !== null,
    loadStatus: state.loaded ? 'ready' : query.isError && !query.isFetching ? 'error' : 'loading',
    loadErrorMessage: query.error?.message ?? null,
    reload,
    state,
    setContent: controller.setContent,
    retry: controller.retry,
    resolveWithServer: controller.resolveWithServer,
    overwriteWithMine: controller.overwriteWithMine,
    continueWithDraft: controller.continueWithDraft,
    discardDraft: controller.discardDraft,
  };
}
