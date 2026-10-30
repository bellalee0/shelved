// Google Identity Services(GIS) 스크립트 로딩과 사용하는 최소 타입.
// npm 래퍼 라이브러리 대신 Google이 제공하는 스크립트를 직접 불러온다.
// 타입은 공식 문서(https://developers.google.com/identity/gsi/web/reference/js-reference)에서 쓰는 부분만 옮겼다.

const GOOGLE_IDENTITY_SCRIPT_URL = 'https://accounts.google.com/gsi/client';

/** frontend/.env.example의 예시 값. 이 값이면 아직 실제 client ID를 설정하지 않은 것으로 본다 */
const EXAMPLE_GOOGLE_CLIENT_ID = '000000000000-example.apps.googleusercontent.com';

export type GoogleCredentialResponse = {
  /** Google이 서명한 ID 토큰(JWT). 백엔드 POST /api/auth/google의 idToken으로 보낸다 */
  credential: string;
};

type GoogleIdConfiguration = {
  client_id: string;
  callback: (response: GoogleCredentialResponse) => void;
};

type GoogleButtonConfiguration = {
  type?: 'standard' | 'icon';
  theme?: 'outline' | 'filled_blue' | 'filled_black';
  size?: 'large' | 'medium' | 'small';
  text?: 'signin_with' | 'signup_with' | 'continue_with' | 'signin';
  shape?: 'rectangular' | 'pill' | 'circle' | 'square';
  locale?: string;
  width?: number;
};

export type GoogleAccountsId = {
  initialize: (config: GoogleIdConfiguration) => void;
  renderButton: (parent: HTMLElement, options: GoogleButtonConfiguration) => void;
};

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } };
  }
}

let scriptPromise: Promise<GoogleAccountsId> | null = null;

/** 실제 Google client ID가 설정되었는지. 비어 있거나 예시 값이면 false */
export function isGoogleClientIdConfigured(clientId: string | undefined): clientId is string {
  const trimmed = clientId?.trim();
  return !!trimmed && trimmed !== EXAMPLE_GOOGLE_CLIENT_ID;
}

/** GIS 스크립트를 한 번만 불러온다. 실패하면 다음 호출에서 다시 시도할 수 있게 비운다 */
export function loadGoogleIdentity(): Promise<GoogleAccountsId> {
  if (window.google?.accounts?.id) {
    return Promise.resolve(window.google.accounts.id);
  }
  scriptPromise ??= new Promise<GoogleAccountsId>((resolve, reject) => {
    const script = document.createElement('script');
    script.src = GOOGLE_IDENTITY_SCRIPT_URL;
    script.async = true;
    script.defer = true;
    script.onload = () => {
      const accountsId = window.google?.accounts?.id;
      if (accountsId) {
        resolve(accountsId);
      } else {
        reject(new Error('Google 로그인 스크립트를 초기화하지 못했습니다.'));
      }
    };
    script.onerror = () => {
      script.remove();
      reject(new Error('Google 로그인 스크립트를 불러오지 못했습니다.'));
    };
    document.head.appendChild(script);
  }).catch((error: unknown) => {
    scriptPromise = null;
    throw error;
  });
  return scriptPromise;
}
