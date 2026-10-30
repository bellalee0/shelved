import type { Location } from 'react-router';

/**
 * 로그인·닉네임 화면으로 보낼 때 함께 넘기는 라우터 state. from은 돌아갈 경로(pathname + search + hash)다.
 * 2주차 메인 화면에서 비로그인 사용자를 로그인으로 보낼 때도 이 형식을 쓰면 로그인 후 같은 화면으로 돌아온다.
 */
export type AuthRedirectState = {
  from: string;
};

// 로그인 흐름 안의 화면으로는 돌아가지 않는다
const AUTH_FLOW_PATHS = ['/login', '/nickname'];

export function createAuthRedirectState(location: Pick<Location, 'pathname' | 'search' | 'hash'>) {
  return {
    from: `${location.pathname}${location.search}${location.hash}`,
  } satisfies AuthRedirectState;
}

/** 라우터 state에서 돌아갈 경로를 꺼낸다. 없거나 앱 내부 경로가 아니면 메인('/')으로 보낸다 */
export function getRedirectPath(state: unknown): string {
  const from =
    typeof state === 'object' && state !== null && 'from' in state ? state.from : undefined;
  // '//example.com'처럼 다른 사이트로 나가는 주소는 받지 않는다
  if (typeof from !== 'string' || !from.startsWith('/') || from.startsWith('//')) {
    return '/';
  }
  const pathname = from.split(/[?#]/)[0];
  return AUTH_FLOW_PATHS.includes(pathname) ? '/' : from;
}
