import { http } from 'msw';
import type { EvidenceInspectResponse } from '../../features/evidence/types';
import { findMockCase } from '../data/clocktowerCase';
import { checkOperable, loadOwnedRoom } from '../roomAccess';
import { saveMockRoomDb } from '../roomDb';
import { checkScenario } from '../scenarios';
import { mockErrorResponse, ok } from './auth';

// docs/api/investigation.md 의 물증 조사. 같은 물증은 몇 번 조사해도 같은 설명을 돌려준다.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const evidenceHandlers = [
  http.post(
    `${API_BASE_URL}/api/rooms/:roomId/evidences/:evidenceId/inspect`,
    ({ request, params }) => {
      const scenarioResponse = checkScenario('other');
      if (scenarioResponse) {
        return scenarioResponse;
      }
      const loaded = loadOwnedRoom(request, params.roomId);
      if ('error' in loaded) {
        return loaded.error;
      }
      saveMockRoomDb();
      // 세션 상태에 따른 공통 실패 순서: 연결 헤더 → 진행 중 → 시간 → 연결 일치
      const operableError = checkOperable(request, loaded);
      if (operableError) {
        return operableError;
      }
      const evidence = findMockCase(loaded.room.caseId)?.evidences.find(
        (candidate) => candidate.evidenceId === Number(params.evidenceId),
      );
      if (!evidence) {
        return mockErrorResponse(404, 'EVIDENCE_NOT_FOUND', '물증이 존재하지 않습니다.');
      }
      const data: EvidenceInspectResponse = {
        evidenceId: evidence.evidenceId,
        name: evidence.name,
        description: evidence.description,
      };
      return ok(data, '물증 조사 성공');
    },
  ),
];
