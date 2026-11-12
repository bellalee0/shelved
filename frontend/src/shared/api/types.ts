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

// AI 요청 처리 상태 (docs/api/room.md "AiRequestStatusResponse").
// NPC 질문·힌트·지목이 함께 쓰는 공통 응답 객체라 shared에 둔다.

/** AI 요청에 플레이어가 보낸 입력. NPC_ASK·HINT·HINT_FOLLOWUP은 question, ACCUSATION은 suspectId·trickExplanation */
export type AiRequestInput = { question: string } | { suspectId: number; trickExplanation: string };

/**
 * AI 요청의 처리 상태. AI POST의 202 응답, 상태 조회 API, 세션 복원 응답에서 쓴다.
 * result는 요청 유형별 응답(NpcAskResponse 등)이라 unknown으로 받고, 쓰는 도메인이 형식을 확인한다.
 */
export type AiRequestStatusResponse = {
  requestId: string;
  type: AiRequestType;
  /** NPC_ASK: suspectId, HINT_FOLLOWUP: hintId, 그 외 null */
  targetId: number | null;
  status: AiRequestStatus;
  input: AiRequestInput;
  /** SUCCEEDED일 때 최초 성공 당시 응답의 스냅샷. 현재 횟수·상태는 GET state로 다시 조회한다 */
  result: unknown;
  /** FAILED일 때 */
  error: { code: string; message: string } | null;
  /** ACCUSATION이 FAILED일 때 같은 답안 재채점이 가능한 시각 */
  retryAvailableAt: string | null;
  acceptedAt: string;
  updatedAt: string;
};

export type GradingStatus = 'PENDING' | 'COMPLETED' | 'ERROR';

export type Verdict = 'CORRECT' | 'PARTIAL' | 'INCORRECT';

export type MessageSenderType = 'USER' | 'NPC';

export type AdminMessageSenderType = 'ADMIN' | 'AI';

export type HotspotType = 'SUSPECT' | 'EVIDENCE';

export type LlmRequestType =
  'NPC_CHAT' | 'HINT' | 'HINT_FOLLOWUP' | 'GRADING' | 'CASE_GENERATION' | 'QUESTION_FILTER';
