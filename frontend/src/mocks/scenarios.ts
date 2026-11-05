import { HttpResponse } from 'msw';
import { mockErrorResponse } from './handlers/auth';

// 오류 재현 스위치. 정상 흐름으로는 나오지 않는 상황(조회 실패, 네트워크 오류)을 개발 중에 켠다.
// 켜진 스위치는 localStorage의 `shelved.mock.scenario`(JSON 배열)에 저장되어 새로고침 후에도 유지된다.
// 브라우저 콘솔에서는 window.shelvedMock.setScenario(...)로 켜고 끈다 (mocks/devTools.ts, README 참고).
// 연결 교체·노트 충돌처럼 "일어난 사건"은 스위치가 아니라 devTools의 동작 함수로 재현한다.

const SCENARIO_STORAGE_KEY = 'shelved.mock.scenario';

export const SCENARIO_NAMES = ['casesError', 'roomsError', 'networkError'] as const;
export type ScenarioName = (typeof SCENARIO_NAMES)[number];

/** 켜진 스위치 목록. 저장소를 못 읽거나 형식이 다르면 모두 꺼진 것으로 본다 */
export function getEnabledScenarios(): ScenarioName[] {
  try {
    const saved: unknown = JSON.parse(localStorage.getItem(SCENARIO_STORAGE_KEY) ?? '[]');
    return Array.isArray(saved)
      ? SCENARIO_NAMES.filter((name) => (saved as unknown[]).includes(name))
      : [];
  } catch {
    return [];
  }
}

export function setScenario(name: ScenarioName, enabled: boolean) {
  const next = new Set(getEnabledScenarios());
  if (enabled) {
    next.add(name);
  } else {
    next.delete(name);
  }
  localStorage.setItem(SCENARIO_STORAGE_KEY, JSON.stringify([...next]));
}

export function clearScenarios() {
  localStorage.removeItem(SCENARIO_STORAGE_KEY);
}

/**
 * 핸들러 맨 앞에서 부른다. 켜진 스위치에 해당하면 돌려줄 응답을 만들고, 아니면 null이다.
 * - networkError: 모든 사건·세션·물증·노트 요청이 네트워크 오류로 실패
 * - casesError: GET /api/cases 가 500
 * - roomsError: GET /api/rooms 가 500
 */
export function checkScenario(target: 'cases' | 'rooms' | 'other'): Response | null {
  const enabled = getEnabledScenarios();
  if (enabled.includes('networkError')) {
    return HttpResponse.error();
  }
  if (
    (target === 'cases' && enabled.includes('casesError')) ||
    (target === 'rooms' && enabled.includes('roomsError'))
  ) {
    return mockErrorResponse(500, 'INTERNAL_SERVER_ERROR', '서버 내부 오류가 발생했습니다.');
  }
  return null;
}
