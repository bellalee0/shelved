import { Link, useLocation, useNavigate } from 'react-router';
import { Button } from '../../../shared/components/Button';
import { useAuth } from '../hooks/useAuth';
import { useLogout } from '../hooks/useLogout';
import { createAuthRedirectState } from '../redirect';

const LINK_CLASS_NAME = 'rounded-md px-3 py-2 text-sm hover:bg-line/40';

/**
 * 상단 사용자 메뉴 (와이어프레임 ② 사용자 메뉴의 최소 형태).
 * 로그인 상태면 닉네임·닉네임 변경·로그아웃, 관리자면 관리자 화면 링크를, 비로그인이면 로그인 링크를 보여준다.
 */
export function UserMenu() {
  const { user, isAdmin } = useAuth();
  const { logout, isPending } = useLogout();
  const location = useLocation();
  const navigate = useNavigate();

  if (!user) {
    return (
      <nav aria-label="사용자 메뉴">
        <Link to="/login" state={createAuthRedirectState(location)} className={LINK_CLASS_NAME}>
          로그인
        </Link>
      </nav>
    );
  }

  async function handleLogout() {
    await logout();
    navigate('/', { replace: true });
  }

  return (
    <nav aria-label="사용자 메뉴" className="flex items-center gap-1">
      <span className="px-3 text-sm font-medium">{user.nickname}</span>
      <Link to="/nickname" className={LINK_CLASS_NAME}>
        닉네임 변경
      </Link>
      {isAdmin && (
        <Link to="/admin" className={LINK_CLASS_NAME}>
          관리자 화면
        </Link>
      )}
      <Button variant="ghost" isLoading={isPending} onClick={handleLogout}>
        로그아웃
      </Button>
    </nav>
  );
}
