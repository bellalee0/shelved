import { isApiError } from '../../shared/api/apiError';
import type { DraftStore, NoteDraft } from './draft';
import type { NoteResponse, NoteRevisionConflictDetail } from './types';

// 노트 자동 저장의 상태 전이. React에 의존하지 않아 단위 테스트할 수 있다.
// 규칙: 한 번에 하나만 보낸다 / 저장 중 입력이 바뀌면 끝난 뒤 최신 내용으로 한 번 더 저장한다 /
// 실패해도 입력은 유지하고 자동으로 무한 재시도하지 않는다 / 충돌은 자동으로 덮어쓰지 않는다.

export type NoteFlushResult = 'SAVED' | 'NO_CHANGES' | 'FAILED';

export type NoteSaveStatus =
  'LOADING' | 'SAVED' | 'SAVING' | 'DIRTY' | 'FAILED' | 'CONFLICT' | 'BLOCKED' | 'TOO_LONG';

export type NoteState = {
  loaded: boolean;
  /** 입력창에 보여 줄 내용 (서버에 저장 전일 수 있다) */
  content: string;
  /** 마지막으로 서버에 저장된(또는 서버에서 받은) 내용 */
  serverContent: string;
  revision: number;
  maxLength: number;
  status: NoteSaveStatus;
  /** 서버에 저장되지 않은 내용이 있는지 */
  dirty: boolean;
  /** 409 NOTE_REVISION_CONFLICT일 때 서버의 최신 내용 */
  conflict: NoteRevisionConflictDetail | null;
  /** 저장이 세션 상태 때문에 거부됐을 때의 에러 코드 */
  blockedCode: string | null;
  /** 처음 불러올 때 발견한, 서버 내용과 다른 이 기기의 초안 */
  pendingDraft: NoteDraft | null;
  operable: boolean;
};

/** 저장이 세션 상태 때문에 거부됐음을 뜻하는 에러 코드 */
const SESSION_REJECT_CODES = new Set([
  'ROOM_NOT_IN_PROGRESS',
  'ROOM_TIME_EXPIRED',
  'CONNECTION_REPLACED',
  'CONNECTION_ID_REQUIRED',
]);

type ServerNote = Pick<NoteResponse, 'content' | 'revision' | 'maxLength'>;

export type AutosaveDeps = {
  save: (content: string, revision: number) => Promise<NoteResponse>;
  delayMs: number;
  draftStore: DraftStore;
  onSessionRejected?: (code: string) => void;
};

export type Autosave = {
  getState: () => NoteState;
  subscribe: (listener: () => void) => () => void;
  /** 서버에서 불러온 노트로 시작한다. 한 번만 적용된다 */
  init: (note: ServerNote) => void;
  /**
   * 재접속으로 서버 노트를 다시 받기 시작한다. 예약된 저장을 취소하고, 다시 맞출 때까지
   * 불러오는 중으로 보이며 입력·저장을 막는다. 진행 중인 저장이 끝나면 resolve된다(그 뒤에 조회해야 최신이다)
   */
  beginResync: () => Promise<void>;
  /**
   * 다시 받은 서버 노트로 맞춘다. 진행 중인 저장이 있으면 끝난 뒤 맞춘다.
   * 서버와 다른 미저장 내용이 있으면 초안으로 남기고 초안 선택(pendingDraft)을 띄운다
   */
  resync: (note: ServerNote) => void;
  setContent: (content: string) => void;
  setOperable: (operable: boolean) => void;
  /** 세션 거부 알림을 받을 함수를 바꾼다 (화면이 다시 그려져도 같은 컨트롤러를 쓰기 위해) */
  setOnSessionRejected: (handler: ((code: string) => void) | undefined) => void;
  flush: () => Promise<NoteFlushResult>;
  /** 실패 상태에서 "다시 시도" */
  retry: () => void;
  resolveWithServer: () => void;
  overwriteWithMine: () => void;
  continueWithDraft: () => void;
  discardDraft: () => void;
  dispose: () => void;
};

type SaveOutcome = { ok: boolean; saved: boolean };

function parseConflict(detail: unknown): NoteRevisionConflictDetail | null {
  if (typeof detail !== 'object' || detail === null) {
    return null;
  }
  const { currentRevision, currentContent } = detail as Record<string, unknown>;
  if (typeof currentRevision === 'number' && typeof currentContent === 'string') {
    return { currentRevision, currentContent };
  }
  return null;
}

export function createAutosave(deps: AutosaveDeps): Autosave {
  const listeners = new Set<() => void>();
  let timer: ReturnType<typeof setTimeout> | null = null;
  let inflight: Promise<SaveOutcome> | null = null;

  // 화면에 보이는 값과 별개로 갖는 내부 플래그
  let loaded = false;
  let content = '';
  let serverContent = '';
  let revision = 0;
  let maxLength = Number.POSITIVE_INFINITY;
  let saving = false;
  let failed = false;
  let invalidInput = false;
  let conflict: NoteRevisionConflictDetail | null = null;
  let blockedCode: string | null = null;
  let pendingDraft: NoteDraft | null = null;
  let operable = false;
  /** 재접속으로 서버 노트를 다시 받는 중 (beginResync ~ resync 적용) */
  let resyncing = false;
  /** 진행 중인 저장이 끝나면 적용할 서버 노트. 여러 번 오면 마지막 것만 쓴다 */
  let resyncNote: ServerNote | null = null;
  let onSessionRejected = deps.onSessionRejected;

  function isDirty() {
    return content !== serverContent;
  }

  function computeStatus(): NoteSaveStatus {
    if (!loaded || resyncing) return 'LOADING';
    if (conflict) return 'CONFLICT';
    if (saving) return 'SAVING';
    if (content.length > maxLength || invalidInput) return 'TOO_LONG';
    if (blockedCode) return 'BLOCKED';
    if (failed) return 'FAILED';
    return isDirty() ? 'DIRTY' : 'SAVED';
  }

  function buildState(): NoteState {
    return {
      loaded: loaded && !resyncing,
      content,
      serverContent,
      revision,
      maxLength,
      status: computeStatus(),
      dirty: isDirty(),
      conflict,
      blockedCode,
      pendingDraft,
      operable,
    };
  }

  let state = buildState();

  function emit() {
    state = buildState();
    listeners.forEach((listener) => listener());
  }

  function clearTimer() {
    if (timer !== null) {
      clearTimeout(timer);
      timer = null;
    }
  }

  function canSend() {
    return (
      loaded &&
      !resyncing &&
      operable &&
      !conflict &&
      !blockedCode &&
      !invalidInput &&
      content.length <= maxLength
    );
  }

  function syncDraft() {
    if (isDirty()) {
      deps.draftStore.write(content, revision);
    } else {
      deps.draftStore.clear();
    }
  }

  function scheduleSave() {
    clearTimer();
    if (!isDirty() || !canSend()) {
      return;
    }
    timer = setTimeout(() => {
      timer = null;
      void startSave();
    }, deps.delayMs);
  }

  function handleSaveError(error: unknown) {
    if (isApiError(error)) {
      if (error.status === 409 && error.code === 'NOTE_REVISION_CONFLICT') {
        const parsed = parseConflict(error.detail);
        if (parsed) {
          conflict = parsed;
          return;
        }
      }
      if (error.code !== null && SESSION_REJECT_CODES.has(error.code)) {
        blockedCode = error.code;
        return;
      }
      if (error.status === 400 && error.code === 'INVALID_INPUT') {
        invalidInput = true;
        return;
      }
    }
    failed = true;
  }

  /** 저장할 내용이 있으면 보낸다. 이미 진행 중인 저장이 있으면 그 결과를 돌려준다(새로 보내지 않는다) */
  function startSave(): Promise<SaveOutcome> | null {
    if (inflight) {
      return inflight;
    }
    if (!isDirty() || !canSend()) {
      return null;
    }
    const current = (async (): Promise<SaveOutcome> => {
      let saved = false;
      let rejectedCode: string | null = null;
      // inflight 대입이 끝난 뒤에 아래 finally가 실행되도록 한 틱 양보한다
      await null;
      try {
        // 저장 중에 입력이 바뀌었으면 같은 반복에서 최신 내용으로 한 번 더 보낸다
        while (isDirty() && canSend()) {
          const sentContent = content;
          saving = true;
          failed = false;
          emit();
          try {
            const response = await deps.save(sentContent, revision);
            serverContent = sentContent;
            revision = response.revision;
            maxLength = response.maxLength;
            saved = true;
            saving = false;
            syncDraft();
            emit();
          } catch (error) {
            saving = false;
            handleSaveError(error);
            if (blockedCode) {
              rejectedCode = blockedCode;
            }
            emit();
            return { ok: false, saved };
          }
        }
        return { ok: true, saved };
      } finally {
        saving = false;
        inflight = null;
        emit();
        if (rejectedCode) {
          onSessionRejected?.(rejectedCode);
        }
      }
    })();
    inflight = current;
    return current;
  }

  /** resync의 실제 적용. 진행 중인 저장이 없을 때만 부른다 */
  function applyResync() {
    const note = resyncNote;
    if (!note || !resyncing) {
      return;
    }
    resyncNote = null;
    // 이 화면의 미저장 내용: 고르지 않은 초안 > 입력 중 내용 > 이 기기에 보관된 초안
    const storedDraft = deps.draftStore.read();
    const unsaved: NoteDraft | null =
      pendingDraft ??
      (isDirty() ? { content, baseRevision: revision, savedAt: Date.now() } : null) ??
      storedDraft;

    resyncing = false;
    loaded = true;
    content = note.content;
    serverContent = note.content;
    revision = note.revision;
    maxLength = note.maxLength;
    conflict = null;
    failed = false;
    invalidInput = false;
    if (unsaved && unsaved.content !== note.content) {
      // 서버 내용을 기준으로 삼고, 미저장 내용은 초안으로 남겨 이어 쓸지 버릴지 고르게 한다
      pendingDraft = unsaved;
      deps.draftStore.write(unsaved.content, unsaved.baseRevision);
    } else {
      pendingDraft = null;
      deps.draftStore.clear();
    }
    emit();
  }

  return {
    getState: () => state,
    subscribe(listener) {
      listeners.add(listener);
      return () => listeners.delete(listener);
    },
    init(note) {
      if (loaded) {
        return;
      }
      loaded = true;
      content = note.content;
      serverContent = note.content;
      revision = note.revision;
      maxLength = note.maxLength;
      const draft = deps.draftStore.read();
      if (draft && draft.content !== note.content) {
        pendingDraft = draft;
      } else if (draft) {
        deps.draftStore.clear();
      }
      emit();
    },
    beginResync() {
      clearTimer();
      resyncing = true;
      emit();
      const pending = inflight;
      return pending ? pending.then(() => undefined) : Promise.resolve();
    },
    resync(note) {
      clearTimer();
      resyncing = true;
      resyncNote = note;
      const pending = inflight;
      if (pending) {
        void pending.then(applyResync);
      } else {
        applyResync();
      }
    },
    setContent(next) {
      // 이 기기의 미저장 초안을 이어 쓸지 버릴지 고르기 전에는 입력을 받지 않는다(초안을 덮어쓰지 않기 위해).
      // 서버 노트를 다시 받는 중에도 받지 않는다
      if (!loaded || resyncing || pendingDraft) {
        return;
      }
      content = next;
      failed = false;
      invalidInput = false;
      syncDraft();
      emit();
      scheduleSave();
    },
    setOnSessionRejected(handler) {
      onSessionRejected = handler;
    },
    setOperable(next) {
      if (operable === next) {
        return;
      }
      operable = next;
      if (next) {
        // 다시 조작할 수 있게 되면 세션 거부 상태를 풀고, 저장 안 된 내용이 있으면 한 번 저장한다
        blockedCode = null;
        failed = false;
        emit();
        clearTimer();
        void startSave();
      } else {
        clearTimer();
        emit();
      }
    },
    async flush() {
      let saved = false;
      for (let attempt = 0; attempt < 3; attempt += 1) {
        clearTimer();
        const pending = startSave();
        if (pending) {
          const outcome = await pending;
          saved = saved || outcome.saved;
          if (!outcome.ok) {
            return 'FAILED';
          }
          continue;
        }
        if (!isDirty()) {
          return saved ? 'SAVED' : 'NO_CHANGES';
        }
        return 'FAILED';
      }
      return isDirty() ? 'FAILED' : saved ? 'SAVED' : 'NO_CHANGES';
    },
    retry() {
      failed = false;
      emit();
      clearTimer();
      void startSave();
    },
    resolveWithServer() {
      if (!conflict) {
        return;
      }
      clearTimer();
      content = conflict.currentContent;
      serverContent = conflict.currentContent;
      revision = conflict.currentRevision;
      conflict = null;
      failed = false;
      deps.draftStore.clear();
      emit();
    },
    overwriteWithMine() {
      if (!conflict) {
        return;
      }
      clearTimer();
      revision = conflict.currentRevision;
      serverContent = conflict.currentContent;
      conflict = null;
      syncDraft();
      emit();
      void startSave();
    },
    continueWithDraft() {
      if (!pendingDraft) {
        return;
      }
      const draftContent = pendingDraft.content;
      pendingDraft = null;
      content = draftContent;
      failed = false;
      invalidInput = false;
      syncDraft();
      emit();
      scheduleSave();
    },
    discardDraft() {
      pendingDraft = null;
      // 그 사이 새로 입력한 내용이 있으면 그 초안은 지우지 않는다
      syncDraft();
      emit();
    },
    dispose() {
      clearTimer();
    },
  };
}
