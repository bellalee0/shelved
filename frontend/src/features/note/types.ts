// 백엔드 note 도메인 DTO와 같은 이름·필드의 타입 (docs/api/investigation.md)

export type NoteResponse = {
  /** 플레이어가 직접 작성한 텍스트 (공백·줄바꿈 포함 원문) */
  content: string;
  /** 저장할 때 함께 보낼 버전 */
  revision: number;
  updatedAt: string;
  /** 최대 글자 수 (서버 설정값, UTF-16 코드 단위) */
  maxLength: number;
  /** IN_PROGRESS이고 시간이 남아 있을 때만 true */
  editable: boolean;
};

export type NoteUpdateRequest = {
  /** 0~maxLength자. 빈 문자열 허용 */
  content: string;
  /** 마지막으로 받은 revision */
  revision: number;
};

/** 409 NOTE_REVISION_CONFLICT 의 detail */
export type NoteRevisionConflictDetail = {
  currentRevision: number;
  currentContent: string;
};
