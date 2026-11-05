// 백엔드 room 도메인 DTO와 같은 이름·필드의 타입 (docs/api/room.md)
import type {
  AiRequestStatus,
  AiRequestType,
  Difficulty,
  GradingStatus,
  RoomStatus,
} from '../../shared/api/types';

/** GET /api/rooms 의 status 쿼리. ACTIVE: READY·IN_PROGRESS·PAUSED, ENDED: CLEARED·FAILED·EXPIRED */
export type RoomListStatus = 'ACTIVE' | 'ENDED';

export type RoomListParams = {
  status?: RoomListStatus;
  page?: number;
  size?: number;
};

/** 메인 화면의 "이어하기 / 결과 보기" 목록 한 줄 */
export type RoomSummaryResponse = {
  roomId: number;
  caseId: number;
  displayTitle: string;
  difficulty: Difficulty;
  coverImageUrl: string | null;
  status: RoomStatus;
  /** 종료 세션은 0 */
  remainingMs: number;
  /** 채점 중(PENDING)·채점 실패(ERROR) 지목이 있으면 표시 */
  pendingGradingStatus: Extract<GradingStatus, 'PENDING' | 'ERROR'> | null;
  /** 최종 종료 시각. 종료 전 null */
  endedAt: string | null;
  /** 기록된 진행 시간(초). 종료 전 null */
  durationSec: number | null;
  createdAt: string;
};

export type RoomCreateRequest = {
  caseId: number;
};

export type RoomCreateResponse = {
  roomId: number;
  caseId: number;
  /** 항상 READY */
  status: RoomStatus;
};

/** 409 ROOM_ALREADY_EXISTS 의 detail */
export type RoomAlreadyExistsDetail = {
  roomId: number;
};

/** 시간 관련 API가 공통으로 돌려준다. 화면 타이머는 이 값을 받을 때마다 다시 맞춘다 */
export type RoomTimerResponse = {
  status: RoomStatus;
  timeLimitSec: number;
  /** 일시정지를 제외한 누적 진행 시간 */
  elapsedActiveMs: number;
  /** 남은 시간 (0 이상) */
  remainingMs: number;
  /** 시간은 소진됐지만 마감 전에 접수한 지목의 채점이 끝나지 않아 최종 결과를 보류 중 */
  awaitingGrading: boolean;
  serverTime: string;
};

export type UsageCount = {
  limit: number;
  /** 사용 확정 */
  used: number;
  /** 처리 중 임시 확보 (0 또는 1) */
  reserved: number;
  /** limit - used - reserved */
  remaining: number;
};

/** AI 요청에 플레이어가 보낸 입력. NPC_ASK·HINT·HINT_FOLLOWUP은 question, ACCUSATION은 suspectId·trickExplanation */
export type AiRequestInput = { question: string } | { suspectId: number; trickExplanation: string };

/**
 * AI 요청의 처리 상태. AI POST의 202 응답, 상태 조회 API, 세션 복원 응답에서 쓴다.
 * 2주차에는 타입만 만든다. result는 요청 유형별 응답(NpcAskResponse 등)이라 해당 도메인 작업에서 좁힌다.
 */
export type AiRequestStatusResponse = {
  requestId: string;
  type: AiRequestType;
  /** NPC_ASK: suspectId, HINT_FOLLOWUP: hintId, 그 외 null */
  targetId: number | null;
  status: AiRequestStatus;
  input: AiRequestInput;
  /** SUCCEEDED일 때 최초 성공 당시 응답의 스냅샷 */
  result: unknown;
  /** FAILED일 때 */
  error: { code: string; message: string } | null;
  /** ACCUSATION이 FAILED일 때 같은 답안 재채점이 가능한 시각 */
  retryAvailableAt: string | null;
  acceptedAt: string;
  updatedAt: string;
};

export type ConversationSummary = {
  suspectId: number;
  /** 양의 정수 */
  messageCount: number;
  lastMessageAt: string;
};

export type RoomStateResponse = {
  roomId: number;
  caseId: number;
  displayTitle: string;
  difficulty: Difficulty;
  /** 관리자 테스트 세션 여부 */
  isTest: boolean;
  /** 최초 "수사 시작" 시각. READY면 null */
  startedAt: string | null;
  timer: RoomTimerResponse;
  heartbeatIntervalMs: number;
  hintCount: UsageCount;
  accusationCount: UsageCount;
  /**
   * 채점 중이거나 채점 실패한 지목 (AccusationResponse, docs/api/hint-accusation.md).
   * 지목 작업에서 정확한 타입으로 바꾼다.
   */
  pendingAccusation: Record<string, unknown> | null;
  /** PROCESSING이거나 재시도 대기 중인 FAILED 요청 */
  aiRequests: AiRequestStatusResponse[];
  conversations: ConversationSummary[];
};

export type RoomBriefingResponse = {
  displayTitle: string;
  difficulty: Difficulty;
  introduction: string;
  investigationGoal: string;
  backgroundKnowledge: string;
  timeLimitSec: number;
  hintLimit: number;
  accusationLimit: number;
};

export type RoomMapSuspect = {
  suspectId: number;
  name: string;
  /** 미배치·대화 불가 인물은 null일 수 있다 */
  spriteKey: string | null;
  /** false여도 지목은 가능 */
  canConverse: boolean;
  /** 미배치 시 x, y 모두 null */
  x: number | null;
  y: number | null;
};

export type RoomMapEvidence = {
  evidenceId: number;
  name: string;
  x: number;
  y: number;
};

export type RoomMapResponse = {
  /** 정적 에셋 경로 (예: /assets/maps/clocktower-mansion.json) */
  mapUrl: string;
  spawnX: number;
  spawnY: number;
  /** 충돌 판정 레이어 이름 */
  collisionLayer: string;
  interactionRadiusPx: number;
  /** 지목 대상 전체 (미배치 인물 포함) */
  suspects: RoomMapSuspect[];
  evidences: RoomMapEvidence[];
};

/** 수사 시작·이어하기 응답. connectionId는 이후 조작 API의 X-Connection-Id 헤더 값이다 */
export type RoomConnectionResponse = {
  connectionId: string;
  heartbeatIntervalMs: number;
  timer: RoomTimerResponse;
};
