// 인증·사용자 API 호출 함수. 이름은 백엔드 AuthController·UserController 메서드명과 맞춘다 (docs/api/auth-user.md)
import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import type {
  AuthGoogleLoginRequest,
  AuthLogoutRequest,
  AuthRefreshRequest,
  AuthTokenResponse,
  UserMeResponse,
  UserNicknameUpdateRequest,
} from './types';

// 로그인·재발급·로그아웃은 액세스 토큰 없이 부르는 API라 skipAuth로 표시한다
// (Authorization 헤더를 붙이지 않고, 401이어도 토큰 재발급을 하지 않는다)

export async function loginWithGoogle(request: AuthGoogleLoginRequest) {
  const response = await apiClient.post<CommonResponse<AuthTokenResponse>>(
    '/api/auth/google',
    request,
    { skipAuth: true },
  );
  return response.data.data;
}

export async function refreshToken(request: AuthRefreshRequest) {
  const response = await apiClient.post<CommonResponse<AuthTokenResponse>>(
    '/api/auth/refresh',
    request,
    { skipAuth: true },
  );
  return response.data.data;
}

export async function logout(request: AuthLogoutRequest) {
  await apiClient.post<CommonResponse<null>>('/api/auth/logout', request, { skipAuth: true });
}

export async function getMyInfo() {
  const response = await apiClient.get<CommonResponse<UserMeResponse>>('/api/users/me');
  return response.data.data;
}

export async function updateNickname(request: UserNicknameUpdateRequest) {
  const response = await apiClient.patch<CommonResponse<UserMeResponse>>(
    '/api/users/me/nickname',
    request,
  );
  return response.data.data;
}
