import { finishMockAiRequest } from '../aiRequestDb';
import { acceptAccusation } from '../accusationDb';
import type { MockRoom } from '../roomDb';

// 테스트 전용: 지목 한 건을 접수해(필요하면 채점까지 끝내) 둔다. 답안은 가상 사건의 것이다.

export function seedAccusation(
  room: MockRoom,
  params: {
    suspectId: number;
    trickExplanation: string;
    /** 'pending'이면 채점 중(상태 조회 pollsUntilDone번 뒤 완료), 'error'면 채점 실패 */
    outcome?: 'completed' | 'pending' | 'error';
    pollsUntilDone?: number;
  },
) {
  const outcome = params.outcome ?? 'completed';
  const request = acceptAccusation(room, {
    requestId: crypto.randomUUID(),
    suspectId: params.suspectId,
    trickExplanation: params.trickExplanation,
    pollsUntilDone: outcome === 'pending' ? (params.pollsUntilDone ?? 1) : null,
    failFirstAttempt: outcome === 'error',
  });
  if (outcome !== 'pending') {
    finishMockAiRequest(room, request);
  }
  return request;
}
