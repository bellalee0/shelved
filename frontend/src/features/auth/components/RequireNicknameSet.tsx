import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { createAuthRedirectState } from '../redirect';

/**
 * 로그인했지만 닉네임이 없는 사용자를 닉네임 화면으로 보낸다. 비로그인 사용자는 그대로 통과한다.
 * 닉네임을 정한 뒤 지금 화면으로 돌아오도록 현재 경로를 state.from에 담는다.
 */
export function RequireNicknameSet() {
  const { nicknameRequired } = useAuth();
  const location = useLocation();

  if (nicknameRequired) {
    return <Navigate to="/nickname" replace state={createAuthRedirectState(location)} />;
  }
  return <Outlet />;
}
