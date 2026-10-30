// 목 모드 전용 가짜 Google ID 토큰 형식. 목 로그인 버튼(만드는 쪽)과 auth 목 핸들러(읽는 쪽)가 함께 쓴다.
// 실제 Google 토큰이 아니며, 실제 백엔드는 이 값을 GOOGLE_TOKEN_INVALID로 거부한다.

const MOCK_ID_TOKEN_PREFIX = 'mock-google-id-token:';

/** googleSub(Google 사용자 식별자)를 담은 가짜 ID 토큰을 만든다 */
export function createMockGoogleIdToken(googleSub: string) {
  return `${MOCK_ID_TOKEN_PREFIX}${googleSub}`;
}

/** 가짜 ID 토큰에서 googleSub를 꺼낸다. 형식이 다르면 null */
export function parseMockGoogleIdToken(idToken: string): string | null {
  if (!idToken.startsWith(MOCK_ID_TOKEN_PREFIX)) {
    return null;
  }
  const googleSub = idToken.slice(MOCK_ID_TOKEN_PREFIX.length);
  return googleSub.length > 0 ? googleSub : null;
}

/** 목 데이터에 미리 들어 있는 관리자 계정의 googleSub */
export const MOCK_ADMIN_GOOGLE_SUB = 'mock-admin';
