import { describe, expect, it } from 'vitest';
import type { AiRequestStatusResponse } from '../../shared/api/types';
import { toAiRequestBadges } from './aiRequestBadges';

function request(
  type: AiRequestStatusResponse['type'],
  status: AiRequestStatusResponse['status'] = 'PROCESSING',
): AiRequestStatusResponse {
  return {
    requestId: `${type}-${status}`,
    type,
    targetId: null,
    status,
    input: { question: '질문' },
    result: null,
    error: null,
    retryAvailableAt: null,
    acceptedAt: '2026-11-16T10:00:00',
    updatedAt: '2026-11-16T10:00:00',
  };
}

describe('toAiRequestBadges', () => {
  it('요청이 없으면 표시하지 않는다', () => {
    expect(toAiRequestBadges([])).toEqual({ hint: false, npc: false, accusation: false });
  });

  it('새 힌트·보충 질문은 힌트 버튼에, NPC 질문은 대화 기록 버튼에 표시한다', () => {
    expect(toAiRequestBadges([request('HINT')])).toEqual({
      hint: true,
      npc: false,
      accusation: false,
    });
    expect(toAiRequestBadges([request('HINT_FOLLOWUP', 'FAILED')])).toEqual({
      hint: true,
      npc: false,
      accusation: false,
    });
    expect(toAiRequestBadges([request('NPC_ASK')])).toEqual({
      hint: false,
      npc: true,
      accusation: false,
    });
  });

  it('채점 중이거나 재채점을 기다리는 지목은 지목 버튼에 표시한다', () => {
    expect(toAiRequestBadges([request('ACCUSATION')])).toEqual({
      hint: false,
      npc: false,
      accusation: true,
    });
    expect(toAiRequestBadges([request('ACCUSATION', 'FAILED')])).toEqual({
      hint: false,
      npc: false,
      accusation: true,
    });
  });

  it('알 수 없는 유형은 무시한다', () => {
    expect(toAiRequestBadges([{ ...request('HINT'), type: 'UNKNOWN' as never }])).toEqual({
      hint: false,
      npc: false,
      accusation: false,
    });
  });

  it('완료된 요청은 표시하지 않는다', () => {
    expect(toAiRequestBadges([request('HINT', 'SUCCEEDED')])).toEqual({
      hint: false,
      npc: false,
      accusation: false,
    });
  });
});
