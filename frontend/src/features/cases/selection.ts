// 메인 화면에서 "고른 사건"을 주소(?case=<caseId>)로 표현한다.
// 비로그인 사용자가 카드를 고르면 이 주소를 로그인 후 돌아올 경로(redirect state.from)로 넘기고,
// 로그인한 사용자는 같은 주소로 이동해 시작 확인 창을 연다. 새로고침해도 선택이 유지된다.

export const SELECTED_CASE_PARAM = 'case';

/** 사건을 고른 메인 화면의 주소 조각. createAuthRedirectState에 그대로 넘길 수 있다 */
export function createCaseSelectionLocation(caseId: number) {
  return { pathname: '/', search: `?${SELECTED_CASE_PARAM}=${caseId}`, hash: '' };
}

/** 쿼리 문자열에서 고른 사건 ID를 꺼낸다. 없거나 양의 정수가 아니면 null */
export function parseSelectedCaseId(searchParams: URLSearchParams): number | null {
  const value = searchParams.get(SELECTED_CASE_PARAM);
  if (value === null || !/^[1-9]\d*$/.test(value)) {
    return null;
  }
  return Number(value);
}
