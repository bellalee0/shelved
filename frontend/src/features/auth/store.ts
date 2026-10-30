import { create } from 'zustand';
import type { UserMeResponse } from './types';

/**
 * 앱 시작 시 로그인 복원 상태
 * - restoring: 저장된 리프레시 토큰으로 복원 중 (화면은 로딩 표시)
 * - ready: 복원이 끝났다 (로그인 여부는 user로 판단)
 * - restoreFailed: 네트워크·서버 오류로 복원하지 못했다. 토큰은 지우지 않고 재시도 안내를 보여준다
 */
export type AuthRestoreStatus = 'restoring' | 'ready' | 'restoreFailed';

type AuthState = {
  /** 메모리에만 두는 액세스 토큰. 새로고침하면 사라지고 리프레시 토큰으로 다시 받는다 */
  accessToken: string | null;
  user: UserMeResponse | null;
  restoreStatus: AuthRestoreStatus;
  /** restoreFailed일 때 보여줄 문구 */
  restoreErrorMessage: string | null;
};

export const INITIAL_AUTH_STATE: AuthState = {
  accessToken: null,
  user: null,
  restoreStatus: 'restoring',
  restoreErrorMessage: null,
};

/**
 * 로그인 상태 전역 저장소. 값을 바꾸는 일은 session.ts가 맡고, 화면은 hooks/useAuth로 읽기만 한다.
 * persist 미들웨어를 쓰지 않는다. 브라우저 저장소에는 리프레시 토큰만 refreshTokenStorage.ts로 따로 둔다.
 */
export const useAuthStore = create<AuthState>()(() => INITIAL_AUTH_STATE);
