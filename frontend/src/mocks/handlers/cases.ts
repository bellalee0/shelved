import { http } from 'msw';
import { MOCK_CASES } from '../data/clocktowerCase';
import { ok } from './auth';
import { checkScenario } from '../scenarios';

// docs/api/cases-ranking.md 의 GET /api/cases. 로그인하지 않아도 부를 수 있다.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

export const casesHandlers = [
  http.get(`${API_BASE_URL}/api/cases`, () => {
    const scenarioResponse = checkScenario('cases');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    return ok(
      MOCK_CASES.map((mockCase) => mockCase.card),
      '사건 목록 조회 성공',
    );
  }),
];
