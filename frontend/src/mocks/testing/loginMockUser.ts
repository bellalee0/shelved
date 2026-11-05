import { loginWithGoogle } from '../../features/auth/api';
import { connectAuthToApiClient } from '../../features/auth/session';
import { INITIAL_AUTH_STATE, useAuthStore } from '../../features/auth/store';
import { createMockGoogleIdToken, MOCK_ADMIN_GOOGLE_SUB } from '../mockGoogleIdToken';

// 테스트 전용: 목 인증으로 실제 로그인해 액세스 토큰을 받고, 로그인 상태 저장소에 반영한다.

/** 닉네임이 있는 목 사용자(셜록초보, userId 2)의 googleSub */
export const MOCK_PLAYER_GOOGLE_SUB = 'mock-existing-player';
export { MOCK_ADMIN_GOOGLE_SUB };

/** 테스트 시작 시 로그인 상태 저장소를 비우고(복원 완료 상태) API 클라이언트에 토큰 getter를 연결한다 */
export function resetLoginState() {
  connectAuthToApiClient();
  useAuthStore.setState({ ...INITIAL_AUTH_STATE, restoreStatus: 'ready' }, true);
}

/** 목 서버에 로그인해 저장소에 토큰·사용자를 넣는다. 돌려주는 값으로 userId를 알 수 있다 */
export async function loginMockUser(googleSub: string = MOCK_PLAYER_GOOGLE_SUB) {
  const { accessToken, user } = await loginWithGoogle({
    idToken: createMockGoogleIdToken(googleSub),
  });
  useAuthStore.setState({ accessToken, user });
  return user;
}
