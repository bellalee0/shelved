import type { RoomStatus } from '../shared/api/types';
import type { RoomSummaryResponse, RoomTimerResponse } from '../features/room/types';
import { findMockCase, MOCK_GAME_CONFIG } from './data/clocktowerCase';

// 세션(방)·노트를 담는 목 "DB"와 상태 전이. 핸들러(handlers/room·evidence·note)가 함께 쓴다.
// 백엔드의 RoomService와 비슷한 일을 하지만 정확한 재현이 목적이 아니라,
// 프론트엔드가 분기해야 하는 응답(409·PAUSED 전환·EXPIRED 등)이 모두 나오게 하는 것이 목적이다.
// 사용자 식별은 인증 목(handlers/auth.ts)의 액세스 토큰 → userId 매핑을 그대로 쓴다.

/** 브라우저 목 모드에서 새로고침 후에도 목 세션을 유지하기 위한 저장 키 (인증 목의 shelved.mock.authDb와 같은 방식) */
const MOCK_ROOM_DB_STORAGE_KEY = 'shelved.mock.roomDb';

type MockNote = {
  content: string;
  revision: number;
  updatedAtMs: number;
};

/** NPC 대화 한 줄 (handlers/npc). messageId는 세션 안에서 유일하다 */
export type MockNpcMessage = {
  messageId: number;
  suspectId: number;
  senderType: 'USER' | 'NPC';
  content: string;
  sequenceNo: number;
  createdAtMs: number;
};

/** 힌트 한 건 (mocks/hintDb.ts). 보충 질문·답변은 같은 힌트 안에 둔다 */
export type MockHint = {
  hintId: number;
  requestId: string;
  question: string;
  content: string;
  createdAtMs: number;
  followup: {
    requestId: string;
    question: string;
    content: string;
    createdAtMs: number;
  } | null;
};

/** AI 요청 한 건의 공통 필드 (mocks/aiRequestDb.ts) */
type MockAiRequestBase = {
  requestId: string;
  /** NPC_ASK: suspectId, HINT_FOLLOWUP: hintId, HINT·ACCUSATION: null */
  targetId: number | null;
  status: 'PROCESSING' | 'SUCCEEDED' | 'FAILED';
  /** SUCCEEDED일 때 최초 성공 당시 응답 (NpcAskResponse 등) */
  result: unknown;
  error: { code: string; message: string } | null;
  /** 실행 번호. 실패 후 같은 requestId로 다시 보내면 올라간다 */
  attemptNo: number;
  /** 처리 중일 때 상태 조회가 몇 번 더 와야 끝나는지. null이면 POST 응답 안에서 끝난다 */
  pollsUntilDone: number | null;
  /** 끝날 때 실패로 끝낼지 (npcFail 스위치의 첫 실행) */
  failOnFinish: boolean;
  acceptedAtMs: number;
  updatedAtMs: number;
  /** ACCUSATION이 FAILED일 때 같은 답안 재채점이 가능한 시각. 그 외 null·없음 */
  retryAvailableAtMs?: number | null;
};

/** 질문을 보내는 AI 요청: NPC 질문·새 힌트·보충 질문 */
export type MockQuestionRequest = MockAiRequestBase & {
  type: 'NPC_ASK' | 'HINT' | 'HINT_FOLLOWUP';
  input: { question: string };
};

/** 지목 채점 요청 (mocks/accusationDb.ts) */
export type MockAccusationRequest = MockAiRequestBase & {
  type: 'ACCUSATION';
  input: { suspectId: number; trickExplanation: string };
};

export type MockAiRequest = MockQuestionRequest | MockAccusationRequest;

/** 지목 한 건 (mocks/accusationDb.ts) */
export type MockAccusation = {
  accusationId: number;
  requestId: string;
  suspectId: number;
  trickExplanation: string;
  submittedAtMs: number;
  gradingStatus: 'PENDING' | 'COMPLETED' | 'ERROR';
  verdict: 'CORRECT' | 'PARTIAL' | 'INCORRECT' | null;
  feedback: string | null;
  attemptNo: number | null;
  retryAvailableAtMs: number | null;
  superseded: boolean;
};

export type MockRoom = {
  roomId: number;
  userId: number;
  caseId: number;
  status: RoomStatus;
  createdAtMs: number;
  /** 최초 "수사 시작" 시각. READY면 null */
  startedAtMs: number | null;
  /** 지난 IN_PROGRESS 구간까지 확정된 진행 시간 */
  elapsedActiveMs: number;
  /** 지금 진행 중인 구간의 시작 시각. IN_PROGRESS가 아니면 null */
  segmentStartedAtMs: number | null;
  /** 마지막 접속 확인(시작·이어하기·하트비트) 시각 */
  lastSeenAtMs: number | null;
  connectionId: string | null;
  endedAtMs: number | null;
  pendingGradingStatus: 'PENDING' | 'ERROR' | null;
  note: MockNote;
  /** NPC 대화 기록. 이 필드가 생기기 전에 저장된 목 세션에는 없으므로 aiRequestDb.ts가 비어 있으면 만든다 */
  npcMessages?: MockNpcMessage[];
  /** AI 요청 기록. npcMessages와 같은 이유로 선택 필드다 */
  aiRequests?: MockAiRequest[];
  /** 힌트 기록. 위 선택 필드와 같은 이유로 선택 필드다 */
  hints?: MockHint[];
  /** 지목 기록. 위 선택 필드와 같은 이유로 선택 필드다 */
  accusations?: MockAccusation[];
};

type MockRoomDb = {
  rooms: MockRoom[];
  nextRoomId: number;
};

const ENDED_STATUSES: RoomStatus[] = ['CLEARED', 'FAILED', 'EXPIRED'];

function createInitialDb(): MockRoomDb {
  return { rooms: [], nextRoomId: 1 };
}

let db: MockRoomDb = createInitialDb();
let persistEnabled = false;
let clock: () => number = () => Date.now();

/** 테스트마다 목 세션 DB와 시계를 처음 상태로 되돌린다 */
export function resetMockRoomDb() {
  db = createInitialDb();
  clock = () => Date.now();
}

/** 시각을 주입한다. 시간이 들어간 테스트에서 시계를 고정할 때 쓴다 (null이면 실제 시각) */
export function setMockClock(nextClock: (() => number) | null) {
  clock = nextClock ?? (() => Date.now());
}

/** 브라우저 목 모드에서만 호출한다. 새로고침해도 목 세션이 유지되도록 localStorage에 저장한다 */
export function enableMockRoomDbPersistence() {
  persistEnabled = true;
  try {
    const saved = localStorage.getItem(MOCK_ROOM_DB_STORAGE_KEY);
    if (saved) {
      db = JSON.parse(saved) as MockRoomDb;
    }
  } catch {
    db = createInitialDb();
  }
}

/** 상태를 바꾼 핸들러가 응답 직전에 호출한다 */
export function saveMockRoomDb() {
  if (persistEnabled) {
    localStorage.setItem(MOCK_ROOM_DB_STORAGE_KEY, JSON.stringify(db));
  }
}

export function currentTimeMs() {
  return clock();
}

/** 서버 현지 시각 형식(오프셋 없는 ISO-8601, 예: 2026-10-05T14:30:00)으로 바꾼다 */
export function toServerTime(ms: number): string {
  const date = new Date(ms);
  const pad = (value: number) => String(value).padStart(2, '0');
  return (
    `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}` +
    `T${pad(date.getHours())}:${pad(date.getMinutes())}:${pad(date.getSeconds())}`
  );
}

function timeLimitMsOf(room: MockRoom): number {
  return (findMockCase(room.caseId)?.card.timeLimitSec ?? 0) * 1000;
}

export function isEnded(status: RoomStatus): boolean {
  return ENDED_STATUSES.includes(status);
}

export function findMockRoom(roomId: number, userId: number): MockRoom | undefined {
  return db.rooms.find((room) => room.roomId === roomId && room.userId === userId);
}

/** 사용자의 세션 전체(최신 생성 순). 호출하기 전에 settleMockRoom으로 시간을 정산한다 */
export function listMockRooms(userId: number): MockRoom[] {
  return db.rooms
    .filter((room) => room.userId === userId)
    .sort((a, b) => b.createdAtMs - a.createdAtMs || b.roomId - a.roomId);
}

/** 같은 사용자·같은 사건의 종료되지 않은 세션 */
export function findUnendedMockRoom(userId: number, caseId: number): MockRoom | undefined {
  return db.rooms.find(
    (room) => room.userId === userId && room.caseId === caseId && !isEnded(room.status),
  );
}

export function createMockRoom(userId: number, caseId: number): MockRoom {
  const nowMs = currentTimeMs();
  const room: MockRoom = {
    roomId: db.nextRoomId,
    userId,
    caseId,
    status: 'READY',
    createdAtMs: nowMs,
    startedAtMs: null,
    elapsedActiveMs: 0,
    segmentStartedAtMs: null,
    lastSeenAtMs: null,
    connectionId: null,
    endedAtMs: null,
    pendingGradingStatus: null,
    // 세션을 만들 때 빈 노트(revision=0)도 함께 만든다
    note: { content: '', revision: 0, updatedAtMs: nowMs },
  };
  db.nextRoomId += 1;
  db.rooms.push(room);
  return room;
}

/**
 * 요청을 받을 때마다 부르는 지연 정산. 백엔드의 이탈 판정·시간 만료 확인을 타이머 대신 계산으로 흉내 낸다.
 * - 마지막 접속 확인이 하트비트 타임아웃(15초)보다 오래됐으면 마지막 접속 확인 시각까지만 누적하고 PAUSED로 바꾼다.
 * - 누적 진행 시간이 제한시간에 이르면 EXPIRED로 바꾼다.
 * 이번 정산에서 처음 만료됐으면 true를 돌려준다 (조작 API가 ROOM_TIME_EXPIRED를 구분하는 데 쓴다).
 */
export function settleMockRoom(room: MockRoom): boolean {
  if (room.status !== 'IN_PROGRESS' || room.segmentStartedAtMs === null) {
    return false;
  }
  const nowMs = currentTimeMs();
  const lastSeenAtMs = room.lastSeenAtMs ?? room.segmentStartedAtMs;
  const isAway = nowMs - lastSeenAtMs > MOCK_GAME_CONFIG.heartbeatTimeoutMs;
  const segmentEndMs = isAway ? lastSeenAtMs : nowMs;
  const elapsedMs = room.elapsedActiveMs + (segmentEndMs - room.segmentStartedAtMs);
  const limitMs = timeLimitMsOf(room);

  if (elapsedMs >= limitMs) {
    expireMockRoom(room, room.segmentStartedAtMs + (limitMs - room.elapsedActiveMs));
    // 채점 대기로 보류됐으면 EXPIRED가 아니다 (조작 API는 isOutOfTime으로 ROOM_TIME_EXPIRED를 준다)
    return (room.status as RoomStatus) === 'EXPIRED';
  }
  if (isAway) {
    room.elapsedActiveMs = elapsedMs;
    room.status = 'PAUSED';
    room.segmentStartedAtMs = null;
    room.connectionId = null;
  }
  return false;
}

/**
 * 마감 전에 접수한 지목의 채점이 끝나지 않았는지 (채점 중이거나, 대체되지 않은 채점 실패).
 * 이때 시간이 끝나면 EXPIRED로 확정하지 않고 결과를 보류한다 (awaitingGrading)
 */
export function hasUnresolvedGrading(room: MockRoom): boolean {
  return (room.accusations ?? []).some(
    (accusation) =>
      !accusation.superseded &&
      (accusation.gradingStatus === 'PENDING' || accusation.gradingStatus === 'ERROR'),
  );
}

function expireMockRoom(room: MockRoom, endedAtMs: number) {
  room.elapsedActiveMs = timeLimitMsOf(room);
  room.segmentStartedAtMs = null;
  room.connectionId = null;
  if (hasUnresolvedGrading(room)) {
    // 시간은 끝났지만 채점 결과를 기다린다. 상태는 그대로 두고 남은 시간 0 + awaitingGrading으로 보여 준다
    return;
  }
  room.status = 'EXPIRED';
  room.endedAtMs = endedAtMs;
}

/** 지목 결과로 세션을 끝낸다 (CLEARED·FAILED, 채점 대기 중이던 세션의 EXPIRED). 진행 중이면 지금까지의 시간을 확정한다 */
export function endMockRoom(room: MockRoom, status: 'CLEARED' | 'FAILED' | 'EXPIRED') {
  const nowMs = currentTimeMs();
  if (room.status === 'IN_PROGRESS' && room.segmentStartedAtMs !== null) {
    room.elapsedActiveMs = Math.min(
      timeLimitMsOf(room),
      room.elapsedActiveMs + (nowMs - room.segmentStartedAtMs),
    );
  }
  room.status = status;
  room.segmentStartedAtMs = null;
  room.connectionId = null;
  room.endedAtMs = nowMs;
}

/** 남은 시간이 0인지 (채점 대기로 보류된 세션 포함) */
export function isOutOfTime(room: MockRoom): boolean {
  return !isEnded(room.status) && remainingMsOf(room) <= 0;
}

/** 지금 시각 기준의 누적 진행 시간. IN_PROGRESS는 진행 중인 구간을 더한다 */
function liveElapsedMs(room: MockRoom): number {
  if (room.status === 'IN_PROGRESS' && room.segmentStartedAtMs !== null) {
    return room.elapsedActiveMs + (currentTimeMs() - room.segmentStartedAtMs);
  }
  return room.elapsedActiveMs;
}

export function remainingMsOf(room: MockRoom): number {
  if (isEnded(room.status)) {
    return 0;
  }
  return Math.max(0, timeLimitMsOf(room) - liveElapsedMs(room));
}

export function timerOf(room: MockRoom): RoomTimerResponse {
  return {
    status: room.status,
    timeLimitSec: timeLimitMsOf(room) / 1000,
    elapsedActiveMs: Math.min(liveElapsedMs(room), timeLimitMsOf(room)),
    remainingMs: remainingMsOf(room),
    awaitingGrading: isOutOfTime(room) && hasUnresolvedGrading(room),
    serverTime: toServerTime(currentTimeMs()),
  };
}

/** 노트를 편집할 수 있는지: IN_PROGRESS이고 시간이 남아 있을 때만 */
export function isNoteEditable(room: MockRoom): boolean {
  return room.status === 'IN_PROGRESS' && remainingMsOf(room) > 0;
}

/** READY → IN_PROGRESS. 새 connectionId를 발급한다 */
export function startMockRoom(room: MockRoom): string {
  const nowMs = currentTimeMs();
  room.status = 'IN_PROGRESS';
  room.startedAtMs = nowMs;
  return beginSegment(room, nowMs);
}

/** PAUSED → IN_PROGRESS. 새 connectionId를 발급해 이전 연결을 무효로 만든다 */
export function resumeMockRoom(room: MockRoom): string {
  room.status = 'IN_PROGRESS';
  return beginSegment(room, currentTimeMs());
}

function beginSegment(room: MockRoom, nowMs: number): string {
  room.segmentStartedAtMs = nowMs;
  room.lastSeenAtMs = nowMs;
  room.connectionId = crypto.randomUUID();
  return room.connectionId;
}

/** 접속 확인 시각을 갱신한다 (하트비트) */
export function touchMockRoom(room: MockRoom) {
  room.lastSeenAtMs = currentTimeMs();
}

/**
 * IN_PROGRESS → PAUSED. 지금까지의 진행 시간을 확정하고 연결을 무효화한다.
 * 제한시간을 소진했으면 EXPIRED가 된다. IN_PROGRESS가 아니면 아무것도 바꾸지 않는다(중복 호출 안전).
 * 호출 전에 settleMockRoom을 거쳐야 한다.
 */
export function pauseMockRoom(room: MockRoom) {
  if (room.status !== 'IN_PROGRESS' || room.segmentStartedAtMs === null) {
    return;
  }
  const nowMs = currentTimeMs();
  const elapsedMs = room.elapsedActiveMs + (nowMs - room.segmentStartedAtMs);
  if (elapsedMs >= timeLimitMsOf(room)) {
    expireMockRoom(room, nowMs);
    if (room.status === 'IN_PROGRESS') {
      // 채점 대기로 보류된 세션은 일시정지 상태로 둔다 (재채점은 연결 없이 보낸다)
      room.status = 'PAUSED';
    }
    return;
  }
  room.elapsedActiveMs = elapsedMs;
  room.status = 'PAUSED';
  room.segmentStartedAtMs = null;
  room.connectionId = null;
}

export function toRoomSummary(room: MockRoom): RoomSummaryResponse {
  const card = findMockCase(room.caseId)!.card;
  const ended = isEnded(room.status);
  return {
    roomId: room.roomId,
    caseId: room.caseId,
    displayTitle: card.displayTitle,
    difficulty: card.difficulty,
    coverImageUrl: card.coverImageUrl,
    status: room.status,
    remainingMs: remainingMsOf(room),
    pendingGradingStatus: room.pendingGradingStatus,
    endedAt: ended && room.endedAtMs !== null ? toServerTime(room.endedAtMs) : null,
    durationSec: ended ? Math.floor(room.elapsedActiveMs / 1000) : null,
    createdAt: toServerTime(room.createdAtMs),
  };
}

// ---- 개발용·테스트용 시드 ----

export type MockRoomSeed = {
  caseId: number;
  status: RoomStatus;
  /** PAUSED일 때 남은 시간(ms). 생략하면 제한시간의 절반 */
  remainingMs?: number;
  pendingGradingStatus?: 'PENDING' | 'ERROR';
  /** 종료 세션일 때 몇 ms 전에 끝났는지. 생략하면 1시간 전 */
  endedAgoMs?: number;
  /** 종료 세션의 진행 시간(초). 생략하면 제한시간의 절반 */
  durationSec?: number;
};

/** 원하는 상태의 세션을 바로 만든다. READY·IN_PROGRESS는 접속 확인 시각이 지금이다 */
export function seedMockRoom(userId: number, seed: MockRoomSeed): MockRoom {
  const room = createMockRoom(userId, seed.caseId);
  const limitMs = timeLimitMsOf(room);
  const nowMs = currentTimeMs();
  room.status = seed.status;
  room.pendingGradingStatus = seed.pendingGradingStatus ?? null;

  if (seed.status === 'READY') {
    return room;
  }
  room.startedAtMs = nowMs;
  if (seed.status === 'IN_PROGRESS') {
    beginSegment(room, nowMs);
  } else if (seed.status === 'PAUSED') {
    room.elapsedActiveMs = limitMs - (seed.remainingMs ?? limitMs / 2);
  } else {
    room.elapsedActiveMs = (seed.durationSec ?? limitMs / 2000) * 1000;
    room.endedAtMs = nowMs - (seed.endedAgoMs ?? 60 * 60 * 1000);
  }
  return room;
}
