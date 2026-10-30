import { Navigate, Outlet, useLocation } from 'react-router';
import { useAuth } from '../hooks/useAuth';
import { createAuthRedirectState } from '../redirect';

/**
 * 로그인이 필요한 화면 묶음(라우트)의 보호막. 비로그인이면 로그인 화면으로 보내고,
 * 로그인한 뒤 지금 화면으로 돌아오도록 현재 경로를 state.from에 담는다.
 */
export function RequireLogin() {
  const { isLoggedIn } = useAuth();
  const location = useLocation();

  if (!isLoggedIn) {
    return <Navigate to="/login" replace state={createAuthRedirectState(location)} />;
  }
  return <Outlet />;
}
