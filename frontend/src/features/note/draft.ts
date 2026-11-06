// 서버에 저장되지 않은 노트 입력을 이 기기의 localStorage에 보관한다. 키는 사용자·세션별이다.
// localStorage가 막혀 있어도(사생활 보호 모드 등) 노트 기능은 동작해야 하므로 모든 접근을 try/catch로 감싼다.
// 노트 내용은 로그·콘솔에 남기지 않는다.

export type NoteDraft = {
  content: string;
  /** 초안을 쓸 때 알고 있던 서버 revision */
  baseRevision: number;
  /** 초안을 마지막으로 보관한 시각 (epoch ms) */
  savedAt: number;
};

export type DraftStore = {
  read: () => NoteDraft | null;
  write: (content: string, baseRevision: number) => void;
  clear: () => void;
};

export function noteDraftKey(userId: number, roomId: number) {
  return `shelved:note-draft:${userId}:${roomId}`;
}

function isNoteDraft(value: unknown): value is NoteDraft {
  if (typeof value !== 'object' || value === null) {
    return false;
  }
  const draft = value as Record<string, unknown>;
  return (
    typeof draft.content === 'string' &&
    typeof draft.baseRevision === 'number' &&
    typeof draft.savedAt === 'number'
  );
}

export function createDraftStore(userId: number, roomId: number): DraftStore {
  const key = noteDraftKey(userId, roomId);
  return {
    read() {
      try {
        const raw = localStorage.getItem(key);
        if (raw === null) {
          return null;
        }
        const parsed: unknown = JSON.parse(raw);
        return isNoteDraft(parsed) ? parsed : null;
      } catch {
        return null;
      }
    },
    write(content, baseRevision) {
      try {
        const draft: NoteDraft = { content, baseRevision, savedAt: Date.now() };
        localStorage.setItem(key, JSON.stringify(draft));
      } catch {
        // 초안 보관만 생략하고 노트 기능은 계속 동작한다
      }
    },
    clear() {
      try {
        localStorage.removeItem(key);
      } catch {
        // 위와 같다
      }
    },
  };
}
