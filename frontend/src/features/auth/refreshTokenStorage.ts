/**
 * 리프레시 토큰을 보관하는 localStorage 키. 브라우저 저장소에 두는 인증 정보는 이 값 하나뿐이다.
 * (액세스 토큰·사용자 정보는 메모리에만 둔다. frontend.md "인증 정보 보관")
 */
export const REFRESH_TOKEN_STORAGE_KEY = 'shelved.auth.refreshToken';

// 저장소 접근이 막힌 환경(브라우저 설정 등)에서도 앱이 멈추지 않도록, 실패하면 토큰이 없는 것으로 다룬다

export function readRefreshToken(): string | null {
  try {
    return localStorage.getItem(REFRESH_TOKEN_STORAGE_KEY);
  } catch {
    return null;
  }
}

export function saveRefreshToken(token: string) {
  try {
    localStorage.setItem(REFRESH_TOKEN_STORAGE_KEY, token);
  } catch {
    // 저장하지 못하면 새로고침 후 복원만 되지 않는다. 현재 탭의 로그인은 메모리 상태로 유지된다
  }
}

export function removeRefreshToken() {
  try {
    localStorage.removeItem(REFRESH_TOKEN_STORAGE_KEY);
  } catch {
    // 지울 수 없으면 다음 재발급에서 서버가 무효 토큰으로 알려 준다
  }
}
