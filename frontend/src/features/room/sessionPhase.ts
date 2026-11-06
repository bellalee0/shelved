import type { RoomStatus } from '../../shared/api/types';
import type { RoomTimerResponse } from './types';

/**
 * 게임 화면의 흐름 단계 (docs/api/room.md "입장·재접속 흐름").
 * - entering: 입장 중 (pause → state·briefing·map)
 * - enterFailed: 입장 요청 실패. 다시 시도 가능
 * - ready: 수사 준비 화면 ("수사 시작")
 * - paused: 복원 화면 ("수사 이어하기")
 * - playing: 연결이 있고 진행 중
 * - replaced: 다른 화면이 연결을 가져갔다 ("이 화면에서 계속하기")
 * - awaitingGrading: 시간은 끝났고 채점 결과를 기다리는 중 (맵을 그리지 않는다)
 * - ended: 종료 상태. 결과 화면으로 이동한다
 */
export type SessionPhase =
  | 'entering'
  | 'enterFailed'
  | 'ready'
  | 'paused'
  | 'playing'
  | 'replaced'
  | 'awaitingGrading'
  | 'ended';

const ENDED_STATUSES: RoomStatus[] = ['CLEARED', 'FAILED', 'EXPIRED'];

export function isEndedStatus(status: RoomStatus): boolean {
  return ENDED_STATUSES.includes(status);
}

/**
 * 서버 타이머로 다음 단계를 정한다. state·heartbeat·start·resume 응답 모두 이 함수로 분기한다.
 * hasConnection: 이 화면이 지금 유효한 connectionId를 갖고 있는지.
 */
export function decideSessionPhase(timer: RoomTimerResponse, hasConnection: boolean): SessionPhase {
  if (isEndedStatus(timer.status)) {
    return 'ended';
  }
  if (timer.awaitingGrading) {
    return 'awaitingGrading';
  }
  switch (timer.status) {
    case 'READY':
      return 'ready';
    case 'PAUSED':
      // 남은 시간 없이 PAUSED면 이어할 수 없다(resume은 409 ROOM_TIME_EXPIRED). 채점 대기와 같이 보여 준다
      return timer.remainingMs > 0 ? 'paused' : 'awaitingGrading';
    default:
      // IN_PROGRESS인데 이 화면에 연결이 없으면 다른 화면(탭)이 진행 중이다
      return hasConnection ? 'playing' : 'replaced';
  }
}
