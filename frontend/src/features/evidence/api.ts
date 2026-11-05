import { apiClient } from '../../shared/api/client';
import type { CommonResponse } from '../../shared/api/types';
import type { EvidenceInspectResponse } from './types';

/** 물증 조사. 거리 검사는 Phaser가 하고, X-Connection-Id가 필요하다 */
export async function inspectEvidence(roomId: number, evidenceId: number) {
  const response = await apiClient.post<CommonResponse<EvidenceInspectResponse>>(
    `/api/rooms/${roomId}/evidences/${evidenceId}/inspect`,
    undefined,
    { requiresConnectionId: true },
  );
  return response.data.data;
}
