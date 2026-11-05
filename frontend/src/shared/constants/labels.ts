import type { Difficulty, RoomStatus } from '../api/types';

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
