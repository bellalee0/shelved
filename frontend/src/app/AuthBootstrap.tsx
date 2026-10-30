import type { ReactNode } from 'react';
import { useAuthBootstrap } from '../features/auth/hooks/useAuthBootstrap';
import { ErrorMessage } from '../shared/components/ErrorMessage';
import { LoadingIndicator } from '../shared/components/LoadingIndicator';

type AuthBootstrapProps = {
  children: ReactNode;
};

/**
 * 앱 시작 시 인증 초기화(토큰 getter 등록, 로그인 복원)를 하는 유일한 곳.
 * 복원이 끝날 때까지 화면 대신 로딩을 보여주고, 네트워크·서버 오류로 복원하지 못하면 토큰을 지우지 않고 재시도 안내를 보여준다.
 */
export function AuthBootstrap({ children }: AuthBootstrapProps) {
  const { restoreStatus, restoreErrorMessage, retryRestore } = useAuthBootstrap();

  if (restoreStatus === 'restoring') {
    return (
      <div className="flex min-h-screen items-center justify-center">
        <LoadingIndicator label="로그인 정보를 확인하는 중…" />
      </div>
    );
  }
  if (restoreStatus === 'restoreFailed') {
    return (
      <div className="mx-auto flex min-h-screen max-w-md items-center px-6">
        <ErrorMessage
          message={restoreErrorMessage ?? '로그인 정보를 확인하지 못했습니다.'}
          onRetry={() => void retryRestore()}
        />
      </div>
    );
  }
  return children;
}
