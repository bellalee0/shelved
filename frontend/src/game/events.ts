// React ↔ Phaser 연동 계약 (frontend.md "이벤트 계약", 기획서 5.2절).
// 이벤트 이름 문자열은 이 파일에만 쓰고, 다른 곳에서는 GameEvents 상수를 쓴다.
export const GameEvents = {
  INTERACT_REQUESTED: 'INTERACT_REQUESTED',
  MAP_READY: 'MAP_READY',
  MAP_LOAD_FAILED: 'MAP_LOAD_FAILED',
  SET_INPUT_ENABLED: 'SET_INPUT_ENABLED',
  PLACE_AT_SPAWN: 'PLACE_AT_SPAWN',
} as const;

export type GameEventName = (typeof GameEvents)[keyof typeof GameEvents];

export type InteractTargetType = 'SUSPECT' | 'EVIDENCE';

export type GameEventPayloads = {
  /** Phaser → React. E키로 고른 대상. targetId는 suspectId 또는 evidenceId */
  [GameEvents.INTERACT_REQUESTED]: { type: InteractTargetType; targetId: number };
  /** Phaser → React. 맵·에셋 준비와 시작점 배치가 끝났다 */
  [GameEvents.MAP_READY]: void;
  /** Phaser → React. 맵 JSON·타일셋·스프라이트 로드 실패. React가 오류 안내와 다시 시도를 보여 준다 */
  [GameEvents.MAP_LOAD_FAILED]: void;
  /**
   * React → Phaser. 이동·상호작용 입력 허용 여부. Phaser는 막힌 상태로 시작한다.
   * Investigation Scene은 MAP_READY를 보낸 뒤부터 구독하므로, React는 MAP_READY를 받은 뒤에 보낸다.
   */
  [GameEvents.SET_INPUT_ENABLED]: { enabled: boolean };
  /** React → Phaser. 플레이어를 사건 시작점으로 옮기고 멈춘다 (최초 배치는 Scene이 직접 한다) */
  [GameEvents.PLACE_AT_SPAWN]: void;
};
