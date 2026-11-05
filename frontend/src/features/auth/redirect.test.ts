import { describe, expect, it } from 'vitest';
import { getRedirectPath } from './redirect';

describe('getRedirectPath', () => {
  it('state.from의 앱 내부 경로를 돌려준다', () => {
    // given
    const state = { from: '/rooms/3?view=notes' };

    // when
    const result = getRedirectPath(state);

    // then
    expect(result).toBe('/rooms/3?view=notes');
  });

  it('사건을 고른 메인 화면 주소(?case=)도 쿼리까지 그대로 돌려준다', () => {
    // given
    const state = { from: '/?case=2' };

    // when
    const result = getRedirectPath(state);

    // then
    expect(result).toBe('/?case=2');
  });

  it('값이 없거나 다른 사이트 주소·로그인 흐름 화면이면 메인으로 보낸다', () => {
    // given
    const states = [
      null,
      {},
      { from: 'https://example.com' },
      { from: '//example.com' },
      { from: '/login' },
      { from: '/nickname?x=1' },
    ];

    // when
    const results = states.map(getRedirectPath);

    // then
    expect(results).toEqual(['/', '/', '/', '/', '/', '/']);
  });
});
