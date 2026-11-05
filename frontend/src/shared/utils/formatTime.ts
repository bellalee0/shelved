const pad = (value: number) => String(value).padStart(2, '0');

/** 밀리초를 분:초로 바꾼다. 소수 초는 버린다 (예: 768750 → "12:48"). 음수는 0으로 본다 */
export function formatRemainingTime(remainingMs: number): string {
  const totalSec = Math.floor(Math.max(0, remainingMs) / 1000);
  return `${pad(Math.floor(totalSec / 60))}:${pad(totalSec % 60)}`;
}

/** 초를 분:초로 바꾼다 (예: 912 → "15:12") */
export function formatDuration(durationSec: number): string {
  return formatRemainingTime(durationSec * 1000);
}

/**
 * 서버 시각(오프셋 없는 ISO-8601, 예: "2026-10-04T20:11:00")을 "10/04 20:11"로 바꾼다.
 * 서버 현지 시각을 그대로 보여 주므로 Date로 변환하지 않는다. 형식이 다르면 원문을 돌려준다.
 */
export function formatServerDateTime(serverTime: string): string {
  const match = /^\d{4}-(\d{2})-(\d{2})T(\d{2}):(\d{2})/.exec(serverTime);
  return match ? `${match[1]}/${match[2]} ${match[3]}:${match[4]}` : serverTime;
}

/** 제한시간(초)을 "20분"처럼 보여 준다. 분 단위로 나누어떨어지지 않으면 초를 함께 쓴다 */
export function formatTimeLimit(timeLimitSec: number): string {
  const minutes = Math.floor(timeLimitSec / 60);
  const seconds = timeLimitSec % 60;
  return seconds === 0 ? `${minutes}분` : `${minutes}분 ${seconds}초`;
}
