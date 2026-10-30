import { useLocation, useNavigate } from 'react-router';
import { NicknameForm } from '../features/auth/components/NicknameForm';
import { useAuth } from '../features/auth/hooks/useAuth';
import { getRedirectPath } from '../features/auth/redirect';

/** 닉네임 설정·변경 화면(/nickname). 저장하면 원래 가려던 화면(없으면 메인)으로 이동한다 */
export function NicknamePage() {
  const location = useLocation();
  const navigate = useNavigate();
  const { user } = useAuth();
  const redirectPath = getRedirectPath(location.state);

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col justify-center gap-6 px-6 py-16">
      <div>
        <h1 className="text-2xl font-bold">닉네임 설정</h1>
        <p className="mt-2 text-muted">랭킹에 표시될 이름을 정해 주세요</p>
      </div>
      <NicknameForm
        initialNickname={user?.nickname ?? ''}
        onSuccess={() => navigate(redirectPath, { replace: true })}
      />
    </main>
  );
}
