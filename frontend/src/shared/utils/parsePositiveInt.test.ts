import { describe, expect, it } from 'vitest';
import { parsePositiveInt } from './parsePositiveInt';

describe('parsePositiveInt', () => {
  it('양의 정수 문자열만 숫자로 바꾼다', () => {
    // given
    const values = ['12', '0', '-1', '1.5', '1e3', 'abc', '', undefined, null];

    // when
    const results = values.map(parsePositiveInt);

    // then
    expect(results).toEqual([12, null, null, null, null, null, null, null, null]);
  });
});
