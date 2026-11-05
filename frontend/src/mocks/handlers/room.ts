import { http } from 'msw';
import type {
  RoomConnectionResponse,
  RoomCreateRequest,
  RoomCreateResponse,
  RoomListStatus,
  RoomMapResponse,
  RoomStateResponse,
  RoomSummaryResponse,
} from '../../features/room/types';
import type { PageResponse } from '../../shared/api/types';
import { findMockCase, MOCK_GAME_CONFIG } from '../data/clocktowerCase';
import { authenticatePlayer, connectionReplacedResponse, loadOwnedRoom } from '../roomAccess';
import {
  type MockRoom,
  createMockRoom,
  findUnendedMockRoom,
  isEnded,
  listMockRooms,
  pauseMockRoom,
  resumeMockRoom,
  saveMockRoomDb,
  settleMockRoom,
  startMockRoom,
  timerOf,
  toRoomSummary,
  toServerTime,
  touchMockRoom,
  remainingMsOf,
} from '../roomDb';
import { checkScenario } from '../scenarios';
import { mockErrorResponse, ok } from './auth';

// docs/api/room.md 의 세션 API 중 2주차 범위(목록·생성·state·briefing·map·start·heartbeat·pause·resume).
// result·ai-requests 는 이후 작업에서 추가한다.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const DEFAULT_PAGE_SIZE = 20;

function parseListStatus(value: string | null): RoomListStatus | null {
  return value === 'ACTIVE' || value === 'ENDED' ? value : null;
}

function parseNonNegativeInt(value: string | null, fallback: number): number | null {
  if (value === null) {
    return fallback;
  }
  const parsed = Number(value);
  return Number.isInteger(parsed) && parsed >= 0 ? parsed : null;
}

export const roomHandlers = [
  // 내 세션 목록: 최신 생성 순, status 필터, 페이지네이션
  http.get(`${API_BASE_URL}/api/rooms`, ({ request }) => {
    const scenarioResponse = checkScenario('rooms');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const player = authenticatePlayer(request);
    if ('error' in player) {
      return player.error;
    }
    const url = new URL(request.url);
    const statusParam = url.searchParams.get('status');
    const status = parseListStatus(statusParam);
    const page = parseNonNegativeInt(url.searchParams.get('page'), 0);
    const size = parseNonNegativeInt(url.searchParams.get('size'), DEFAULT_PAGE_SIZE);
    if ((statusParam !== null && !status) || page === null || size === null || size < 1) {
      return mockErrorResponse(400, 'INVALID_PARAMETER', '파라미터 값이 올바르지 않습니다.', {
        field: statusParam !== null && !status ? 'status' : 'page',
      });
    }

    const rooms = listMockRooms(player.userId);
    rooms.forEach(settleMockRoom);
    saveMockRoomDb();
    const filtered = rooms.filter((room) => {
      if (!status) {
        return true;
      }
      return status === 'ENDED' ? isEnded(room.status) : !isEnded(room.status);
    });
    const content = filtered.slice(page * size, (page + 1) * size).map(toRoomSummary);
    const body: PageResponse<RoomSummaryResponse> = {
      content,
      totalElements: filtered.length,
      totalPages: Math.ceil(filtered.length / size),
      size,
      number: page,
    };
    return ok(body, '세션 목록 조회 성공');
  }),

  // 세션 생성: 같은 사건의 미종료 세션이 있으면 409 ROOM_ALREADY_EXISTS + detail.roomId
  http.post(`${API_BASE_URL}/api/rooms`, async ({ request }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const player = authenticatePlayer(request);
    if ('error' in player) {
      return player.error;
    }
    const body = (await request.json().catch(() => ({}))) as Partial<RoomCreateRequest>;
    if (!Number.isInteger(body.caseId) || (body.caseId ?? 0) < 1) {
      return mockErrorResponse(400, 'INVALID_INPUT', '사건 ID는 필수입니다.', { field: 'caseId' });
    }
    const caseId = body.caseId!;
    if (!findMockCase(caseId)) {
      return mockErrorResponse(404, 'CASE_NOT_FOUND', '사건이 존재하지 않습니다.');
    }

    // 시간이 지나 이미 끝난 세션은 종료로 정산한 뒤 중복 여부를 본다
    listMockRooms(player.userId).forEach(settleMockRoom);
    const existing = findUnendedMockRoom(player.userId, caseId);
    if (existing) {
      saveMockRoomDb();
      return mockErrorResponse(
        409,
        'ROOM_ALREADY_EXISTS',
        '이 사건에 진행 중인 수사가 있습니다. 이어서 진행해 주세요.',
        { roomId: existing.roomId },
      );
    }

    const room = createMockRoom(player.userId, caseId);
    saveMockRoomDb();
    const data: RoomCreateResponse = { roomId: room.roomId, caseId, status: room.status };
    return ok(data, '세션 생성 성공', 201);
  }),

  http.get(`${API_BASE_URL}/api/rooms/:roomId/state`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    const { room } = loaded;
    const card = findMockCase(room.caseId)!.card;
    const data: RoomStateResponse = {
      roomId: room.roomId,
      caseId: room.caseId,
      displayTitle: card.displayTitle,
      difficulty: card.difficulty,
      isTest: false,
      startedAt: room.startedAtMs === null ? null : toServerTime(room.startedAtMs),
      timer: timerOf(room),
      heartbeatIntervalMs: MOCK_GAME_CONFIG.heartbeatIntervalMs,
      // 2주차에는 힌트·지목을 쓰지 않으므로 사용 0, 한도만 설정값
      hintCount: usageOf(card.hintLimit),
      accusationCount: usageOf(card.accusationLimit),
      pendingAccusation: null,
      aiRequests: [],
      conversations: [],
    };
    return ok(data, '세션 상태 조회 성공');
  }),

  http.get(`${API_BASE_URL}/api/rooms/:roomId/briefing`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    return ok(findMockCase(loaded.room.caseId)!.briefing, '사전 정보 조회 성공');
  }),

  http.get(`${API_BASE_URL}/api/rooms/:roomId/map`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    const { map, suspects, evidences } = findMockCase(loaded.room.caseId)!;
    const data: RoomMapResponse = {
      ...map,
      suspects,
      // 물증 설명은 조사 API 응답으로만 내려준다
      evidences: evidences.map(({ evidenceId, name, x, y }) => ({ evidenceId, name, x, y })),
    };
    return ok(data, '맵 조회 성공');
  }),

  // 수사 시작: READY에서만 가능, 새 connectionId 발급
  http.post(`${API_BASE_URL}/api/rooms/:roomId/start`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    const { room } = loaded;
    if (room.status !== 'READY') {
      return mockErrorResponse(409, 'ROOM_NOT_READY', '이미 시작된 수사입니다.');
    }
    const connectionId = startMockRoom(room);
    saveMockRoomDb();
    return ok(connectionResponseOf(connectionId, room), '수사 시작 성공');
  }),

  // 접속 확인: 헤더 필수, IN_PROGRESS가 아니면 연결 검사 없이 현재 상태만 반환
  http.post(`${API_BASE_URL}/api/rooms/:roomId/heartbeat`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    const { room } = loaded;
    const connectionId = request.headers.get('X-Connection-Id');
    if (!connectionId) {
      return mockErrorResponse(
        400,
        'CONNECTION_ID_REQUIRED',
        '수사 연결 정보가 없습니다. 게임 화면을 다시 열어 주세요.',
      );
    }
    if (room.status === 'IN_PROGRESS') {
      if (connectionId !== room.connectionId) {
        return connectionReplacedResponse();
      }
      touchMockRoom(room);
    }
    saveMockRoomDb();
    return ok(timerOf(room), '접속 확인 성공');
  }),

  // 일시정지: 헤더를 요구하지 않고, IN_PROGRESS가 아니면 아무것도 바꾸지 않아 중복 호출해도 안전하다
  http.post(`${API_BASE_URL}/api/rooms/:roomId/pause`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    pauseMockRoom(loaded.room);
    saveMockRoomDb();
    return ok(timerOf(loaded.room), '일시정지 성공');
  }),

  // 이어하기: PAUSED에서만 가능, 남은 시간이 없으면 거부, 새 connectionId로 이전 연결을 무효화
  http.post(`${API_BASE_URL}/api/rooms/:roomId/resume`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    const { room } = loaded;
    if (room.status !== 'PAUSED') {
      return mockErrorResponse(
        409,
        'ROOM_NOT_PAUSED',
        '일시정지 상태의 수사만 이어할 수 있습니다.',
      );
    }
    if (remainingMsOf(room) <= 0) {
      return mockErrorResponse(409, 'ROOM_TIME_EXPIRED', '제한시간이 끝났습니다.');
    }
    const connectionId = resumeMockRoom(room);
    saveMockRoomDb();
    return ok(connectionResponseOf(connectionId, room), '수사 이어하기 성공');
  }),
];

function usageOf(limit: number) {
  return { limit, used: 0, reserved: 0, remaining: limit };
}

function connectionResponseOf(connectionId: string, room: MockRoom): RoomConnectionResponse {
  return {
    connectionId,
    heartbeatIntervalMs: MOCK_GAME_CONFIG.heartbeatIntervalMs,
    timer: timerOf(room),
  };
}
