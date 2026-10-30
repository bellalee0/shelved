// 테스트 전용: jsdom에는 Web Locks API(navigator.locks)가 없으므로, 같은 이름의 잠금을 순서대로 실행하는 대체 구현을 넣는다.

/** navigator.locks를 대체 구현으로 바꾸고, 요청된 잠금 이름 목록과 원래대로 되돌리는 함수를 돌려준다 */
export function installFakeWebLocks() {
  const requestedNames: string[] = [];
  const queues = new Map<string, Promise<unknown>>();

  const fakeLocks = {
    request(name: string, callback: () => Promise<unknown>) {
      requestedNames.push(name);
      const previous = queues.get(name) ?? Promise.resolve();
      const run = previous.then(() => callback());
      queues.set(
        name,
        run.catch(() => undefined),
      );
      return run;
    },
  };
  Object.defineProperty(navigator, 'locks', { value: fakeLocks, configurable: true });

  return {
    requestedNames,
    uninstall: () => Reflect.deleteProperty(navigator, 'locks'),
  };
}
