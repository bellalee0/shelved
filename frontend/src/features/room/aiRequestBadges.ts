import type { AiRequestStatusResponse } from '../../shared/api/types';

// 재접속·새로고침 직후 GET state의 aiRequests로 "어느 창을 열어 보면 되는지" 알려 주는 HUD 표시용 순수 함수

export type AiRequestBadges = {
  /** 새 힌트·보충 질문 요청이 처리 중이거나 다시 보내기를 기다린다 → 힌트 버튼 */
  hint: boolean;
  /** NPC 질문이 처리 중이거나 다시 보내기를 기다린다 → 대화 기록 버튼 */
  npc: boolean;
  /** 지목이 채점 중이거나 같은 답안 재채점을 기다린다 → 지목 버튼 */
  accusation: boolean;
};

/** 유형별로 처리 중·재시도 대기 요청이 있는지 본다 */
export function toAiRequestBadges(aiRequests: AiRequestStatusResponse[]): AiRequestBadges {
  const pending = aiRequests.filter(
    (request) => request.status === 'PROCESSING' || request.status === 'FAILED',
  );
  return {
    hint: pending.some((request) => request.type === 'HINT' || request.type === 'HINT_FOLLOWUP'),
    npc: pending.some((request) => request.type === 'NPC_ASK'),
    accusation: pending.some((request) => request.type === 'ACCUSATION'),
  };
}
