import { describe, expect, it } from 'vitest';
import {
  formatDuration,
  formatRemainingTime,
  formatServerDateTime,
  formatTimeLimit,
} from './formatTime';

describe('formatRemainingTime', () => {
  it('남은 시간을 분:초 형식으로 바꾸고 소수 초는 버린다', () => {
    // given
    const remainingMs = 768_750;

    // when
    const result = formatRemainingTime(remainingMs);

    // then
    expect(result).toBe('12:48');
  });

  it('0이나 음수는 00:00이다', () => {
    // given
    const values = [0, -500];

    // when
    const results = values.map(formatRemainingTime);

    // then
    expect(results).toEqual(['00:00', '00:00']);
  });
});

describe('formatDuration', () => {
  it('초를 분:초 형식으로 바꾼다', () => {
    // given
    const durationSec = 912;

    // when
    const result = formatDuration(durationSec);

    // then
    expect(result).toBe('15:12');
  });
});

describe('formatServerDateTime', () => {
  it('서버 시각을 월/일 시:분으로 바꾼다', () => {
    // given
    const serverTime = '2026-10-04T20:11:09';

    // when
    const result = formatServerDateTime(serverTime);

    // then
    expect(result).toBe('10/04 20:11');
  });

  it('형식이 다르면 원문을 돌려준다', () => {
    // given
    const serverTime = '어제';

    // when
    const result = formatServerDateTime(serverTime);

    // then
    expect(result).toBe('어제');
  });
});

describe('formatTimeLimit', () => {
  it('분 단위로 나누어떨어지면 분만, 아니면 초를 함께 보여 준다', () => {
    // given
    const values = [1200, 1230];

    // when
    const results = values.map(formatTimeLimit);

    // then
    expect(results).toEqual(['20분', '20분 30초']);
  });
});
