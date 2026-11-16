import type { AiRequestStatusResponse } from '../../shared/api/types';

// 재접속·새로고침 직후 GET state의 aiRequests로 "어느 창을 열어 보면 되는지" 알려 주는 HUD 표시용 순수 함수

export type AiRequestBadges = {
  /** 새 힌트·보충 질문 요청이 처리 중이거나 다시 보내기를 기다린다 → 힌트 버튼 */
  hint: boolean;
  /** NPC 질문이 처리 중이거나 다시 보내기를 기다린다 → 대화 기록 버튼 */
  npc: boolean;
};

/** 지목(ACCUSATION)을 포함해 알 수 없는 유형은 무시한다 (지목 UI는 4주차) */
export function toAiRequestBadges(aiRequests: AiRequestStatusResponse[]): AiRequestBadges {
  const pending = aiRequests.filter(
    (request) => request.status === 'PROCESSING' || request.status === 'FAILED',
  );
  return {
    hint: pending.some((request) => request.type === 'HINT' || request.type === 'HINT_FOLLOWUP'),
    npc: pending.some((request) => request.type === 'NPC_ASK'),
  };
}
