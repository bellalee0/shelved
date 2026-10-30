import { afterEach, describe, expect, it } from 'vitest';
import { AUTH_LOCK_NAME, withAuthLock } from './authLock';
import { installFakeWebLocks } from './testing/fakeWebLocks';

afterEach(() => {
  Reflect.deleteProperty(navigator, 'locks');
});

/** 실행 순서를 기록하며, 잠깐 기다렸다 끝나는 작업 */
function createTask(log: string[], name: string) {
  return async () => {
    log.push(`${name} 시작`);
    await new Promise((resolve) => setTimeout(resolve, 5));
    log.push(`${name} 끝`);
    return name;
  };
}

describe('withAuthLock', () => {
  it('Web Locks가 있으면 shelved-auth 잠금으로 작업을 실행한다', async () => {
    // given
    const fakeLocks = installFakeWebLocks();

    // when
    const result = await withAuthLock(async () => '완료');

    // then
    expect(result).toBe('완료');
    expect(fakeLocks.requestedNames).toEqual([AUTH_LOCK_NAME]);
  });

  it('Web Locks가 없으면 같은 탭 안에서 작업을 하나씩 순서대로 실행한다', async () => {
    // given
    expect('locks' in navigator).toBe(false);
    const log: string[] = [];

    // when
    await Promise.all([withAuthLock(createTask(log, 'A')), withAuthLock(createTask(log, 'B'))]);

    // then
    expect(log).toEqual(['A 시작', 'A 끝', 'B 시작', 'B 끝']);
  });

  it('Web Locks가 없을 때 앞의 작업이 실패해도 다음 작업은 실행된다', async () => {
    // given
    const failing = withAuthLock(async () => {
      throw new Error('실패');
    });

    // when
    const next = withAuthLock(async () => '다음 작업');

    // then
    await expect(failing).rejects.toThrow('실패');
    await expect(next).resolves.toBe('다음 작업');
  });
});
