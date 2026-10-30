import { useEffect, useRef, useState } from 'react';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { isGoogleClientIdConfigured, loadGoogleIdentity } from '../googleIdentity';

type GoogleLoginButtonProps = {
  /** Google이 돌려준 ID 토큰(credential)을 받는다 */
  onCredential: (idToken: string) => void;
};

const GOOGLE_CLIENT_ID = import.meta.env.VITE_GOOGLE_CLIENT_ID;

/**
 * Google Identity Services가 그리는 "Google 계정으로 로그인" 버튼.
 * VITE_GOOGLE_CLIENT_ID가 비어 있거나 .env.example 예시 값이면 버튼 대신 설정 안내를 보여준다.
 */
export function GoogleLoginButton({ onCredential }: GoogleLoginButtonProps) {
  if (!isGoogleClientIdConfigured(GOOGLE_CLIENT_ID)) {
    return (
      <p className="rounded-md border border-line bg-surface px-4 py-3 text-sm text-muted">
        Google 로그인이 설정되지 않았습니다. <code>frontend/.env.local</code>의{' '}
        <code>VITE_GOOGLE_CLIENT_ID</code>에 Google OAuth 클라이언트 ID를 넣은 뒤 개발 서버를 다시
        실행해 주세요.
      </p>
    );
  }
  return <GoogleButtonSlot clientId={GOOGLE_CLIENT_ID} onCredential={onCredential} />;
}

type GoogleButtonSlotProps = GoogleLoginButtonProps & {
  clientId: string;
};

function GoogleButtonSlot({ clientId, onCredential }: GoogleButtonSlotProps) {
  const containerRef = useRef<HTMLDivElement>(null);
  const [status, setStatus] = useState<'loading' | 'ready' | 'failed'>('loading');
  const [loadAttempt, setLoadAttempt] = useState(0);
  // Google 콜백은 initialize 때 한 번 등록되므로, 최신 onCredential을 ref로 넘겨 다시 초기화하지 않게 한다
  const onCredentialRef = useRef(onCredential);
  useEffect(() => {
    onCredentialRef.current = onCredential;
  }, [onCredential]);

  useEffect(() => {
    let isActive = true;
    loadGoogleIdentity()
      .then((accountsId) => {
        if (!isActive || !containerRef.current) {
          return;
        }
        accountsId.initialize({
          client_id: clientId,
          callback: (response) => onCredentialRef.current(response.credential),
        });
        accountsId.renderButton(containerRef.current, {
          theme: 'outline',
          size: 'large',
          text: 'signin_with',
          locale: 'ko',
          width: 320,
        });
        setStatus('ready');
      })
      .catch(() => {
        if (isActive) {
          setStatus('failed');
        }
      });
    return () => {
      isActive = false;
    };
  }, [clientId, loadAttempt]);

  return (
    <div className="flex flex-col items-center gap-3">
      {status === 'loading' && <LoadingIndicator label="Google 로그인 버튼을 불러오는 중…" />}
      {status === 'failed' && (
        <ErrorMessage
          message="Google 로그인 버튼을 불러오지 못했습니다. 네트워크 상태를 확인해 주세요."
          onRetry={() => {
            setStatus('loading');
            setLoadAttempt((count) => count + 1);
          }}
        />
      )}
      <div ref={containerRef} />
    </div>
  );
}
