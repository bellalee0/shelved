import { Navigate, Outlet } from 'react-router';
import { useAuth } from '../hooks/useAuth';

/**
 * 관리자(role=ADMIN)만 들어갈 수 있는 화면 묶음의 보호막. 권한이 없으면 메인으로 보낸다.
 * 로그인 여부는 바깥의 RequireLogin이 먼저 확인한다. 화면을 숨기는 것은 편의일 뿐이며 권한 검사는 백엔드가 한다.
 */
export function RequireAdmin() {
  const { isAdmin } = useAuth();

  if (!isAdmin) {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
