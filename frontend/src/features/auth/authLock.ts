/** 여러 탭이 같이 쓰는 Web Locks 잠금 이름 (docs/api/auth-user.md "여러 탭의 토큰 갱신 조정") */
export const AUTH_LOCK_NAME = 'shelved-auth';

// Web Locks가 없는 환경에서 같은 탭 안의 작업만 순서대로 실행하기 위한 대기열
let localQueue: Promise<unknown> = Promise.resolve();

/**
 * 토큰 저장·재발급·로그아웃 작업을 탭 간에 한 번에 하나씩 실행한다.
 * - Web Locks API가 있으면 같은 origin의 모든 탭이 `shelved-auth` 잠금을 순서대로 잡는다.
 * - 없으면(MVP 대상이 아닌 오래된 브라우저·비보안 컨텍스트) 같은 탭 안에서만 순서를 지킨다.
 *   이 경우 탭 간 경쟁은 INVALID_REFRESH_TOKEN 시 저장값을 다시 비교하는 규칙으로만 보완된다.
 * 작업 안에서 다시 withAuthLock을 부르면 잠금을 기다리다 멈추므로 중첩해서 쓰지 않는다.
 */
export function withAuthLock<T>(task: () => Promise<T>): Promise<T> {
  if (typeof navigator !== 'undefined' && navigator.locks) {
    return navigator.locks.request(AUTH_LOCK_NAME, task);
  }
  const run = localQueue.then(task);
  localQueue = run.catch(() => undefined);
  return run;
}
