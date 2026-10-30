import { useAuthStore } from '../store';

/** 현재 로그인 사용자와 자주 쓰는 판단값. 화면은 저장소를 직접 읽지 않고 이 훅을 쓴다 */
export function useAuth() {
  const user = useAuthStore((state) => state.user);
  return {
    user,
    isLoggedIn: user !== null,
    isAdmin: user?.role === 'ADMIN',
    nicknameRequired: user?.nicknameRequired ?? false,
  };
}
