import { lazy, Suspense } from 'react';
import { Link, Navigate, useLocation } from 'react-router';
import { GoogleLoginButton } from '../features/auth/components/GoogleLoginButton';
import { useAuth } from '../features/auth/hooks/useAuth';
import { useLoginWithGoogle } from '../features/auth/hooks/useLoginWithGoogle';
import { getRedirectPath, type AuthRedirectState } from '../features/auth/redirect';
import { ErrorMessage } from '../shared/components/ErrorMessage';
import { LoadingIndicator } from '../shared/components/LoadingIndicator';

// 목 로그인 버튼은 목 모드 개발 서버에서만 불러온다.
// 배포 빌드에서는 import.meta.env.DEV가 false로 바뀌어 이 동적 import가 통째로 빠진다.
const MockLoginButtons =
  import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true'
    ? lazy(() =>
        import('../features/auth/components/MockLoginButtons').then((module) => ({
          default: module.MockLoginButtons,
        })),
      )
    : null;

/**
 * 로그인 화면(/login). 로그인되면(이미 로그인 상태로 들어와도) 닉네임이 없으면 닉네임 화면,
 * 있으면 로그인 전에 가려던 화면(없으면 메인)으로 이동한다.
 */
export function LoginPage() {
  const location = useLocation();
  const { user } = useAuth();
  const login = useLoginWithGoogle();
  const redirectPath = getRedirectPath(location.state);

  if (user) {
    return user.nicknameRequired ? (
      <Navigate to="/nickname" replace state={{ from: redirectPath } satisfies AuthRedirectState} />
    ) : (
      <Navigate to={redirectPath} replace />
    );
  }

  return (
    <main className="mx-auto flex min-h-screen max-w-sm flex-col items-center justify-center gap-6 px-6 py-16">
      <div className="text-center">
        <h1 className="text-3xl font-bold">Shelved</h1>
        <p className="mt-2 text-muted">소설 속 사건을 직접 수사하는 추리 게임</p>
      </div>
      <GoogleLoginButton onCredential={(idToken) => login.mutate(idToken)} />
      {MockLoginButtons && (
        <Suspense fallback={<LoadingIndicator />}>
          <MockLoginButtons
            onIdToken={(idToken) => login.mutate(idToken)}
            disabled={login.isPending}
          />
        </Suspense>
      )}
      {login.isPending && <LoadingIndicator label="로그인하는 중…" />}
      {login.error && <ErrorMessage message={login.error.message} />}
      <Link to="/" className="text-sm text-accent underline">
        로그인 없이 사건 둘러보기
      </Link>
    </main>
  );
}
