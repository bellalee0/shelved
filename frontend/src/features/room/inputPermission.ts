import type { RoomStatus } from '../../shared/api/types';

// 입력 허용 조건 (frontend.md "조작·창 관리 규칙"). 맵 이동과 노트 저장이 같은 조건을 공유하도록 한 파일에 둔다.

export type InputConditions = {
  /** Phaser가 MAP_READY를 보냈다 */
  mapReady: boolean;
  /** start·resume으로 받은 connectionId가 있다 */
  hasConnection: boolean;
  /** 다른 화면에서 이어하기를 해서 이 화면의 연결이 무효가 됐다 */
  connectionReplaced: boolean;
  /** 오른쪽 패널·노트 창·나가기 확인 창 중 하나라도 열려 있다 */
  hasOpenWindow: boolean;
  /** 시작·이어하기·연결 교체·채점 대기·오류 오버레이가 떠 있다 */
  hasOverlay: boolean;
  status: RoomStatus | null;
  /** 화면 타이머의 남은 시간. 서버 값을 기준으로 줄여 표시하는 값이다 */
  displayRemainingMs: number | null;
  awaitingGrading: boolean;
};

/**
 * 세션 조작(노트 저장 등)을 보낼 수 있는지. 맵 준비·열린 창과 무관하다
 * (노트 창이 열려 있어도 저장할 수 있어야 한다).
 */
export function canOperateSession(conditions: InputConditions): boolean {
  return (
    conditions.hasConnection &&
    !conditions.connectionReplaced &&
    !conditions.hasOverlay &&
    conditions.status === 'IN_PROGRESS' &&
    conditions.displayRemainingMs !== null &&
    conditions.displayRemainingMs > 0 &&
    !conditions.awaitingGrading
  );
}

/** 맵에서 이동·상호작용(SET_INPUT_ENABLED)을 허용하는지. 세션 조작 조건 + 맵 준비 + 열린 창 없음 */
export function canMoveOnMap(conditions: InputConditions): boolean {
  return conditions.mapReady && !conditions.hasOpenWindow && canOperateSession(conditions);
}
