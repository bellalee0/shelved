// 백엔드 공통 응답·공통 enum과 같은 모양의 타입 (docs/api/README.md)

/** 모든 API 응답을 감싸는 형식. 백엔드 CommonResponse와 같다 */
export type CommonResponse<T> = {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
};

/** 실패 응답의 data */
export type ErrorData = {
  code: string; // 백엔드 ErrorCode 이름 (docs/api/errors.md)
  detail: unknown;
};

/** 페이지 조회 API의 data. 백엔드 PageResponse와 같다 */
export type PageResponse<T> = {
  content: T[];
  totalElements: number;
  totalPages: number;
  size: number;
  number: number;
};

// 공통 enum (docs/api/README.md "공통 enum")

export type UserRole = 'PLAYER' | 'ADMIN';

export type Difficulty = 'EASY' | 'NORMAL' | 'HARD';

/** 관리자 API에서만 노출 */
export type CaseStatus = 'DRAFT' | 'CONTENT_CONFIRMED' | 'PUBLISHED';

/** CLEARED / FAILED / EXPIRED는 종료 상태 */
export type RoomStatus = 'READY' | 'IN_PROGRESS' | 'PAUSED' | 'CLEARED' | 'FAILED' | 'EXPIRED';

export type AiRequestType = 'NPC_ASK' | 'HINT' | 'HINT_FOLLOWUP' | 'ACCUSATION';

export type AiRequestStatus = 'PROCESSING' | 'SUCCEEDED' | 'FAILED';

export type GradingStatus = 'PENDING' | 'COMPLETED' | 'ERROR';

export type Verdict = 'CORRECT' | 'PARTIAL' | 'INCORRECT';

export type MessageSenderType = 'USER' | 'NPC';

export type AdminMessageSenderType = 'ADMIN' | 'AI';

export type HotspotType = 'SUSPECT' | 'EVIDENCE';

export type LlmRequestType =
  'NPC_CHAT' | 'HINT' | 'HINT_FOLLOWUP' | 'GRADING' | 'CASE_GENERATION' | 'QUESTION_FILTER';
