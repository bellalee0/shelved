import { http } from 'msw';
import type { RankingGetResponse } from '../../features/ranking/types';
import { findMockCase } from '../data/clocktowerCase';
import { rankingsOf } from '../rankingDb';
import { authenticatePlayer } from '../roomAccess';
import { checkScenario } from '../scenarios';
import { authenticate, mockErrorResponse, ok } from './auth';

// docs/api/cases-ranking.md 의 GET /api/rankings/{caseId}. 로그인이 필요하다.
// 목 데이터는 mocks/rankingDb.ts 맨 위 주석을 본다.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;
const DEFAULT_PAGE_SIZE = 20;
const MAX_PAGE_SIZE = 100;

export const rankingHandlers = [
  http.get(`${API_BASE_URL}/api/rankings/:caseId`, ({ request, params }) => {
    const scenarioResponse = checkScenario('ranking');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const player = authenticatePlayer(request);
    if ('error' in player) {
      return player.error;
    }
    const caseId = Number(params.caseId);
    const mockCase = findMockCase(caseId);
    if (!mockCase) {
      return mockErrorResponse(404, 'CASE_NOT_FOUND', '사건이 존재하지 않습니다.');
    }
    const url = new URL(request.url);
    const page = Number(url.searchParams.get('page') ?? 0);
    const size = Number(url.searchParams.get('size') ?? DEFAULT_PAGE_SIZE);
    if (
      !Number.isInteger(page) ||
      page < 0 ||
      !Number.isInteger(size) ||
      size < 1 ||
      size > MAX_PAGE_SIZE
    ) {
      return mockErrorResponse(400, 'INVALID_PARAMETER', '파라미터 값이 올바르지 않습니다.', {
        field: 'page',
      });
    }
    // authenticatePlayer를 통과했으므로 사용자와 닉네임이 있다
    const authenticated = authenticate(request);
    const nickname = 'user' in authenticated ? (authenticated.user.nickname ?? '') : '';
    const rankings = rankingsOf(caseId, player.userId, nickname);
    const data: RankingGetResponse = {
      caseId,
      displayTitle: mockCase.card.displayTitle,
      myRanking: rankings.find((ranking) => ranking.nickname === nickname) ?? null,
      rankingPage: {
        content: rankings.slice(page * size, (page + 1) * size),
        totalElements: rankings.length,
        totalPages: Math.ceil(rankings.length / size),
        size,
        number: page,
      },
    };
    return ok(data, '랭킹 조회 성공');
  }),
];
