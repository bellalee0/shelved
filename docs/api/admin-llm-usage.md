# 관리자 AI 사용량 · 예산 API

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 4.3절, 6.5절, 7.5절 기준입니다.
> 모든 API는 `role=ADMIN`만 호출할 수 있습니다.

| Method | 경로 | 설명 |
|---|---|---|
| `GET` | `/api/admin/llm-usage` | 기간별·유형별 사용량과 예상 비용 |
| `GET` | `/api/admin/llm-usage/budget` | 예산 설정 조회 |
| `PATCH` | `/api/admin/llm-usage/budget` | 예산 설정 저장 |

- 통화는 USD 고정, 집계 기준 시간대는 Asia/Seoul입니다.
- 사용량을 확인할 수 없는 호출(`usage_known=false`)은 토큰·비용 합계에 넣지 않고 `unknownUsageCallCount`로 따로 셉니다. 0원으로 간주하지 않습니다.
- 예산 초과는 알림만 하며 AI 호출을 막지 않습니다.

---

## GET /api/admin/llm-usage

**쿼리 파라미터**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `from` | string (`yyyy-MM-dd`) | X | 시작일(포함). 생략 시 이번 달 1일 |
| `to` | string (`yyyy-MM-dd`) | X | 종료일(포함). 생략 시 오늘 |
| `granularity` | `DAY` \| `MONTH` | X | 기간 묶음 단위. 기본 `DAY` |

`from`이 `to`보다 늦거나 기간이 366일을 넘으면 `400 INVALID_INPUT`입니다.

**성공 응답** `200` `LlmUsageResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `currency` | string | `"USD"` |
| `timezone` | string | `"Asia/Seoul"` |
| `from`, `to` | string | 적용된 기간 |
| `total` | [UsageSummary](#usagesummary) | 기간 전체 합계 |
| `byType` | `{ "requestType", ...UsageSummary }[]` | `LlmRequestType`별 합계 |
| `series` | `{ "period", "byType": [...] }[]` | 기간 단위별 합계 (`period`: `DAY`면 `"2026-10-05"`, `MONTH`면 `"2026-10"`) |
| `currentMonth` | [BudgetStatus](#budgetstatus) | 이번 달(달력월) 예산 대비 현황. 조회 기간과 무관 |

### UsageSummary

| 필드 | 타입 | 설명 |
|---|---|---|
| `callCount` | number | 실제 호출 수 (재시도 포함) |
| `failedCallCount` | number | 오류로 끝난 호출 수 |
| `inputTokens` | number | 확인된 사용량 합계 |
| `outputTokens` | number | |
| `estimatedCost` | number | USD, 소수점 4자리 |
| `unknownUsageCallCount` | number | 사용량 미확인 호출 수 |

### BudgetStatus

| 필드 | 타입 | 설명 |
|---|---|---|
| `month` | string | `"2026-10"` |
| `estimatedCost` | number | 이번 달 누적 예상 비용 |
| `unknownUsageCallCount` | number | 이번 달 사용량 미확인 호출 수 |
| `budgetConfigured` | boolean | |
| `monthlyLimit` | number \| null | |
| `usagePercent` | number \| null | `estimatedCost / monthlyLimit * 100`, 소수점 1자리 |
| `alertLevel` | `NONE` \| `WARNING` \| `EXCEEDED` \| `NOT_CONFIGURED` | `WARNING`: 경고 임계값 이상, `EXCEEDED`: 한도 초과. 대시보드 배너 표시 기준 |

---

## GET /api/admin/llm-usage/budget

**성공 응답** `200` `LlmBudgetResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `configured` | boolean | 미설정이면 `false`, 나머지 값은 `null` |
| `monthlyLimit` | number \| null | USD |
| `warningPercent` | number \| null | 1~100 |
| `currency` | string | `"USD"` |
| `timezone` | string | `"Asia/Seoul"` |
| `updatedAt` | string \| null | |

---

## PATCH /api/admin/llm-usage/budget

월 한도와 경고 임계값을 저장합니다. 이번 달 집계에 바로 적용되며 지난달 사용량은 바뀌지 않습니다.

**요청 본문** `LlmBudgetUpdateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `monthlyLimit` | number | O | 0보다 큼, 소수점 2자리까지 |
| `warningPercent` | number | O | 정수 1~100 |

**성공 응답** `200` [LlmBudgetResponse](#get-apiadminllm-usagebudget)

**실패 응답**: `400 INVALID_INPUT`
