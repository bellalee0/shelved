import { http, HttpResponse } from 'msw';
import type {
  AuthGoogleLoginRequest,
  AuthLogoutRequest,
  AuthRefreshRequest,
  AuthTokenResponse,
  UserMeResponse,
  UserNicknameUpdateRequest,
} from '../../features/auth/types';
import type { CommonResponse, ErrorData, UserRole } from '../../shared/api/types';
import { MOCK_ADMIN_GOOGLE_SUB, parseMockGoogleIdToken } from '../mockGoogleIdToken';

// docs/api/auth-user.md의 인증·사용자 API 5개를 흉내 내는 목. 사용자·닉네임은 모두 가상 데이터다.
// 토큰은 실제 JWT가 아닌 임의 문자열이며, 리프레시 토큰은 재발급마다 새로 만들고 이전 값은 무효로 한다(rotation).

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const ACCESS_TOKEN_EXPIRES_IN_SEC = 3600;
const NICKNAME_PATTERN = /^[가-힣A-Za-z0-9]{2,12}$/;
/** 브라우저 목 모드에서 새로고침 후에도 목 사용자·토큰을 유지하기 위한 저장 키 (목 서버의 "DB") */
const MOCK_DB_STORAGE_KEY = 'shelved.mock.authDb';

type MockUser = {
  userId: number;
  googleSub: string;
  nickname: string | null;
  role: UserRole;
};

type MockAuthDb = {
  users: MockUser[];
  /** 유효한 리프레시 토큰 → userId */
  refreshTokens: Record<string, number>;
  /** 발급한 액세스 토큰 → userId */
  accessTokens: Record<string, number>;
  nextUserId: number;
  tokenSequence: number;
};

function createInitialDb(): MockAuthDb {
  return {
    users: [
      { userId: 1, googleSub: MOCK_ADMIN_GOOGLE_SUB, nickname: '관리자', role: 'ADMIN' },
      // 닉네임 중복(409) 확인용 기존 사용자
      { userId: 2, googleSub: 'mock-existing-player', nickname: '셜록초보', role: 'PLAYER' },
    ],
    refreshTokens: {},
    accessTokens: {},
    nextUserId: 3,
    tokenSequence: 0,
  };
}

let db: MockAuthDb = createInitialDb();
let persistEnabled = false;

/** 테스트마다 목 DB를 처음 상태로 되돌린다 */
export function resetMockAuthDb() {
  db = createInitialDb();
}

/** 브라우저 목 모드에서만 호출한다. 새로고침해도 목 사용자·토큰이 유지되도록 localStorage에 목 DB를 저장한다 */
export function enableMockAuthDbPersistence() {
  persistEnabled = true;
  try {
    const saved = localStorage.getItem(MOCK_DB_STORAGE_KEY);
    if (saved) {
      db = JSON.parse(saved) as MockAuthDb;
    }
  } catch {
    db = createInitialDb();
  }
}

function saveDb() {
  if (persistEnabled) {
    localStorage.setItem(MOCK_DB_STORAGE_KEY, JSON.stringify(db));
  }
}

function ok<T>(data: T, message: string) {
  const body: CommonResponse<T> = { success: true, message, data, timestamp: now() };
  return HttpResponse.json(body);
}

/** 명세 형식의 실패 응답을 만든다. 테스트에서 server.use로 오류를 재현할 때도 쓴다 */
export function mockErrorResponse(
  status: number,
  code: string,
  message: string,
  detail: unknown = null,
) {
  const body: CommonResponse<ErrorData> = {
    success: false,
    message,
    data: { code, detail },
    timestamp: now(),
  };
  return HttpResponse.json(body, { status });
}

function now() {
  return new Date().toISOString().slice(0, 23);
}

function toUserMeResponse(user: MockUser): UserMeResponse {
  return {
    userId: user.userId,
    nickname: user.nickname,
    role: user.role,
    nicknameRequired: user.nickname === null,
  };
}

function issueTokens(user: MockUser): AuthTokenResponse {
  db.tokenSequence += 1;
  const accessToken = `mock-access-${user.userId}-${db.tokenSequence}`;
  const refreshToken = `mock-refresh-${user.userId}-${db.tokenSequence}`;
  db.accessTokens[accessToken] = user.userId;
  db.refreshTokens[refreshToken] = user.userId;
  saveDb();
  return {
    accessToken,
    refreshToken,
    accessTokenExpiresInSec: ACCESS_TOKEN_EXPIRES_IN_SEC,
    user: toUserMeResponse(user),
  };
}

/** Authorization 헤더로 로그인 사용자를 찾는다. 실패하면 돌려줄 오류 응답을 담는다 */
function authenticate(request: Request): { user: MockUser } | { error: Response } {
  const authorization = request.headers.get('Authorization');
  if (!authorization?.startsWith('Bearer ')) {
    return { error: mockErrorResponse(401, 'LOGIN_REQUIRED', '로그인이 필요합니다.') };
  }
  const userId = db.accessTokens[authorization.slice('Bearer '.length)];
  const user = db.users.find((candidate) => candidate.userId === userId);
  if (!user) {
    return { error: mockErrorResponse(401, 'INVALID_TOKEN', '유효하지 않은 토큰입니다.') };
  }
  return { user };
}

async function readJson<T>(request: Request): Promise<Partial<T>> {
  try {
    return (await request.json()) as Partial<T>;
  } catch {
    return {};
  }
}

export const authHandlers = [
  http.post(`${API_BASE_URL}/api/auth/google`, async ({ request }) => {
    const { idToken } = await readJson<AuthGoogleLoginRequest>(request);
    if (!idToken) {
      return mockErrorResponse(400, 'INVALID_INPUT', 'ID 토큰은 필수입니다.', { field: 'idToken' });
    }
    const googleSub = parseMockGoogleIdToken(idToken);
    if (!googleSub) {
      return mockErrorResponse(
        401,
        'GOOGLE_TOKEN_INVALID',
        'Google 로그인 정보를 확인할 수 없습니다. 다시 시도해 주세요.',
      );
    }
    let user = db.users.find((candidate) => candidate.googleSub === googleSub);
    if (!user) {
      // 처음 로그인한 사용자는 PLAYER, 닉네임 없음으로 만든다
      user = { userId: db.nextUserId, googleSub, nickname: null, role: 'PLAYER' };
      db.nextUserId += 1;
      db.users.push(user);
    }
    return ok(issueTokens(user), '로그인 성공');
  }),

  http.post(`${API_BASE_URL}/api/auth/refresh`, async ({ request }) => {
    const { refreshToken } = await readJson<AuthRefreshRequest>(request);
    const userId = refreshToken ? db.refreshTokens[refreshToken] : undefined;
    const user = db.users.find((candidate) => candidate.userId === userId);
    if (!refreshToken || !user) {
      return mockErrorResponse(
        401,
        'INVALID_REFRESH_TOKEN',
        '로그인 정보가 만료되었습니다. 다시 로그인해 주세요.',
      );
    }
    // rotation: 사용한 리프레시 토큰은 바로 무효로 만든다
    delete db.refreshTokens[refreshToken];
    return ok(issueTokens(user), '토큰 재발급 성공');
  }),

  http.post(`${API_BASE_URL}/api/auth/logout`, async ({ request }) => {
    // 이미 무효이거나 없는 토큰이어도 200이다
    const { refreshToken } = await readJson<AuthLogoutRequest>(request);
    if (refreshToken) {
      delete db.refreshTokens[refreshToken];
      saveDb();
    }
    return ok(null, '로그아웃 성공');
  }),

  http.get(`${API_BASE_URL}/api/users/me`, ({ request }) => {
    const result = authenticate(request);
    if ('error' in result) {
      return result.error;
    }
    return ok(toUserMeResponse(result.user), '내 정보 조회 성공');
  }),

  http.patch(`${API_BASE_URL}/api/users/me/nickname`, async ({ request }) => {
    const result = authenticate(request);
    if ('error' in result) {
      return result.error;
    }
    const nickname = (await readJson<UserNicknameUpdateRequest>(request)).nickname?.trim() ?? '';
    if (!NICKNAME_PATTERN.test(nickname)) {
      return mockErrorResponse(
        400,
        'INVALID_INPUT',
        '닉네임은 2~12자의 한글, 영문, 숫자만 사용할 수 있습니다.',
        { field: 'nickname' },
      );
    }
    // 영문 대소문자를 구분하지 않고 중복을 검사하며, 본인의 기존 닉네임은 허용한다
    const isTaken = db.users.some(
      (candidate) =>
        candidate.userId !== result.user.userId &&
        candidate.nickname?.toLowerCase() === nickname.toLowerCase(),
    );
    if (isTaken) {
      return mockErrorResponse(409, 'NICKNAME_EXIST', '이미 사용 중인 닉네임입니다.');
    }
    result.user.nickname = nickname;
    saveDb();
    return ok(toUserMeResponse(result.user), '닉네임 변경 성공');
  }),
];
