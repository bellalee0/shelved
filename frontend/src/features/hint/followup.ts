import type { RoomStatus } from '../../shared/api/types';

// 보충 질문 버튼을 누를 수 있는 조건 (docs/api/hint-accusation.md, dev-checklist "보충 질문 전송 버튼").
// followupAvailable은 "기회가 남았는지"만 말하고 세션 상태는 모르므로, 세션 조건을 함께 본다.

export type FollowupConditions = {
  /** 해당 힌트의 최신(방금 GET hints로 받은) followupAvailable */
  followupAvailable: boolean;
  /** GET state의 timer.status. 아직 상태를 받지 못했으면 null */
  timerStatus: RoomStatus | null;
  /** 남은 시간(ms). 화면 타이머 값이며 모르면 null */
  remainingMs: number | null;
  /** 이 화면이 현재 조작 연결을 가지고 있다 (연결 교체·미연결이면 false) */
  hasConnection: boolean;
};

export type FollowupBlockReason = 'NOT_IN_PROGRESS' | 'TIME_OVER' | 'NO_CONNECTION' | 'UNAVAILABLE';

/** 보낼 수 없는 이유. 보낼 수 있으면 null. 세션 조건부터 확인한다 */
export function followupBlockReason(conditions: FollowupConditions): FollowupBlockReason | null {
  if (conditions.timerStatus !== 'IN_PROGRESS') {
    return 'NOT_IN_PROGRESS';
  }
  if (conditions.remainingMs === null || conditions.remainingMs <= 0) {
    return 'TIME_OVER';
  }
  if (!conditions.hasConnection) {
    return 'NO_CONNECTION';
  }
  if (!conditions.followupAvailable) {
    return 'UNAVAILABLE';
  }
  return null;
}

export function canSendFollowup(conditions: FollowupConditions): boolean {
  return followupBlockReason(conditions) === null;
}

/** 비활성화한 버튼 옆에 보여 줄 짧은 안내 */
export const FOLLOWUP_BLOCK_MESSAGES: Record<FollowupBlockReason, string> = {
  NOT_IN_PROGRESS: '수사가 진행 중일 때만 보충 질문을 보낼 수 있습니다.',
  TIME_OVER: '남은 시간이 없어 보충 질문을 보낼 수 없습니다.',
  NO_CONNECTION: '다른 곳에서 수사를 이어하고 있어 보충 질문을 보낼 수 없습니다.',
  UNAVAILABLE: '이 힌트의 보충 질문은 지금 보낼 수 없습니다.',
};
