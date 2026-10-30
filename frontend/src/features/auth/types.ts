// 백엔드 auth·user 도메인 DTO와 같은 이름·필드의 타입 (docs/api/auth-user.md)
import type { UserRole } from '../../shared/api/types';

export type AuthGoogleLoginRequest = {
  /** Google Identity Services가 돌려준 ID 토큰(credential) */
  idToken: string;
};

export type AuthRefreshRequest = {
  refreshToken: string;
};

export type AuthLogoutRequest = {
  refreshToken: string;
};

export type UserMeResponse = {
  userId: number;
  /** 미설정 시 null */
  nickname: string | null;
  role: UserRole;
  /** 닉네임 설정이 필요한지 (nickname == null) */
  nicknameRequired: boolean;
};

export type AuthTokenResponse = {
  accessToken: string;
  refreshToken: string;
  accessTokenExpiresInSec: number;
  user: UserMeResponse;
};

export type UserNicknameUpdateRequest = {
  nickname: string;
};
