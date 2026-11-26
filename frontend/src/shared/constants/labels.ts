import type { Difficulty, RoomStatus, Verdict } from '../api/types';

// 공통 enum의 화면 표시 문구. 여러 도메인(사건 카드, 세션 목록)이 함께 쓴다.

export const DIFFICULTY_LABELS: Record<Difficulty, string> = {
  EASY: '쉬움',
  NORMAL: '보통',
  HARD: '어려움',
};

export const ROOM_STATUS_LABELS: Record<RoomStatus, string> = {
  READY: '시작 전',
  IN_PROGRESS: '진행 중',
  PAUSED: '일시정지',
  CLEARED: '해결',
  FAILED: '실패',
  EXPIRED: '시간 만료',
};

/** 지목 판정 (docs/api/hint-accusation.md). 판정은 서버가 계산한다 */
export const VERDICT_LABELS: Record<Verdict, string> = {
  CORRECT: '정답',
  PARTIAL: '부분 정답',
  INCORRECT: '오답',
};
