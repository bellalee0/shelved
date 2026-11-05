import { authenticate, mockErrorResponse } from './handlers/auth';
import { findMockRoom, settleMockRoom, type MockRoom } from './roomDb';

// 세션을 다루는 핸들러(room·evidence·note)가 공통으로 거치는 검사. 백엔드의 인증 필터 + 세션 조회에 해당한다.

type AuthenticatedPlayer = { userId: number };

/** 로그인과 닉네임 설정 여부를 확인한다. 실패하면 돌려줄 오류 응답을 담는다 */
export function authenticatePlayer(request: Request): AuthenticatedPlayer | { error: Response } {
  const result = authenticate(request);
  if ('error' in result) {
    return result;
  }
  if (result.user.nickname === null) {
    return { error: mockErrorResponse(403, 'NICKNAME_REQUIRED', '닉네임을 먼저 설정해 주세요.') };
  }
  return { userId: result.user.userId };
}

export type LoadedRoom = {
  userId: number;
  room: MockRoom;
  /** 이번 요청을 처리하며 처음 시간 만료로 확인됐는지 */
  expiredNow: boolean;
};

/**
 * 로그인 확인 → 본인 세션 조회 → 시간 정산. 다른 사용자의 세션이나 없는 세션은 똑같이 404 ROOM_NOT_FOUND다.
 */
export function loadOwnedRoom(
  request: Request,
  roomIdParam: string | readonly string[] | undefined,
): LoadedRoom | { error: Response } {
  const player = authenticatePlayer(request);
  if ('error' in player) {
    return player;
  }
  const room = findMockRoom(Number(roomIdParam), player.userId);
  if (!room) {
    return { error: mockErrorResponse(404, 'ROOM_NOT_FOUND', '세션이 존재하지 않습니다.') };
  }
  const expiredNow = settleMockRoom(room);
  return { userId: player.userId, room, expiredNow };
}

/**
 * 조작 API(물증 조사·노트 저장)의 "세션 상태에 따른 공통 실패"를 명세 순서대로 검사한다.
 * 통과하면 null, 아니면 돌려줄 오류 응답이다 (docs/api/investigation.md).
 */
export function checkOperable(request: Request, loaded: LoadedRoom): Response | null {
  if (!request.headers.get('X-Connection-Id')) {
    return mockErrorResponse(
      400,
      'CONNECTION_ID_REQUIRED',
      '수사 연결 정보가 없습니다. 게임 화면을 다시 열어 주세요.',
    );
  }
  if (loaded.expiredNow) {
    return mockErrorResponse(409, 'ROOM_TIME_EXPIRED', '제한시간이 끝났습니다.');
  }
  if (loaded.room.status !== 'IN_PROGRESS') {
    return mockErrorResponse(409, 'ROOM_NOT_IN_PROGRESS', '진행 중인 수사가 아닙니다.');
  }
  if (request.headers.get('X-Connection-Id') !== loaded.room.connectionId) {
    return connectionReplacedResponse();
  }
  return null;
}

export function connectionReplacedResponse() {
  return mockErrorResponse(409, 'CONNECTION_REPLACED', '다른 화면에서 수사를 이어하고 있습니다.');
}
