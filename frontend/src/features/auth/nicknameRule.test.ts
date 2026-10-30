import { describe, expect, it } from 'vitest';
import { getNicknameRuleViolation } from './nicknameRule';

describe('getNicknameRuleViolation', () => {
  it('2~12자의 완성형 한글·영문·숫자는 통과한다', () => {
    // given
    const nicknames = ['왓슨', 'Holmes221', '탐정Kim7', '가나다라마바사아자차카타'];

    // when
    const results = nicknames.map(getNicknameRuleViolation);

    // then
    expect(results).toEqual([null, null, null, null]);
  });

  it('앞뒤 공백은 빼고 길이를 센다', () => {
    // given
    const nickname = '  왓슨  ';

    // when
    const result = getNicknameRuleViolation(nickname);

    // then
    expect(result).toBeNull();
  });

  it('길이가 2자 미만이거나 12자를 넘으면 안내 문구를 돌려준다', () => {
    // given
    const tooShort = '왓';
    const tooLong = '가나다라마바사아자차카타파';

    // when & then
    expect(getNicknameRuleViolation(tooShort)).toContain('2자 이상');
    expect(getNicknameRuleViolation(tooLong)).toContain('12자까지');
  });

  it('공백·특수문자·자음/모음 단독은 안내 문구를 돌려준다', () => {
    // given
    const nicknames = ['셜록 홈즈', '탐정!', 'ㄱㄴㄷ', 'ㅏㅏ'];

    // when
    const results = nicknames.map(getNicknameRuleViolation);

    // then
    results.forEach((result) => expect(result).toContain('완성형 한글·영문·숫자만'));
  });
});
