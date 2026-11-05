import { describe, expect, it } from 'vitest';
import { createCaseSelectionLocation, parseSelectedCaseId } from './selection';

describe('parseSelectedCaseId', () => {
  it('양의 정수 case 값만 사건 ID로 읽는다', () => {
    // given
    const queries = ['?case=2', '?case=0', '?case=abc', '?case=1.5', '?case=-3', '', '?case='];

    // when
    const results = queries.map((query) => parseSelectedCaseId(new URLSearchParams(query)));

    // then
    expect(results).toEqual([2, null, null, null, null, null, null]);
  });
});

describe('createCaseSelectionLocation', () => {
  it('메인 화면에서 사건을 고른 주소를 만든다', () => {
    // given
    const caseId = 7;

    // when
    const location = createCaseSelectionLocation(caseId);

    // then
    expect(location).toEqual({ pathname: '/', search: '?case=7', hash: '' });
  });
});
