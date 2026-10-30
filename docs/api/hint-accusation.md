# 힌트 · 지목 API

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 3.2절, 3.4~3.6절, 6.4절, 7.3절 기준입니다.

| Method | 경로 | 설명 | 연결 헤더 |
|---|---|---|---|
| `GET` | `/api/rooms/{roomId}/hints` | 힌트·보충 질문 기록과 남은 횟수 | |
| `POST` | `/api/rooms/{roomId}/hints` | 새 힌트 요청 (AI, 1회 차감) | 필수 |
| `POST` | `/api/rooms/{roomId}/hints/{hintId}/followup` | 무료 보충 질문 (AI, 힌트당 1회) | 필수 |
| `GET` | `/api/rooms/{roomId}/accusations` | 지목 기록과 남은 횟수 | |
| `POST` | `/api/rooms/{roomId}/accusations` | 범인·트릭 지목 제출 또는 실패한 채점 재시도 (AI) | 새 답안만 필수 |

모든 API는 로그인이 필요합니다. 본인 세션이 아니면 `404 ROOM_NOT_FOUND`입니다. 조작 API의 세션 상태 공통 실패(`CONNECTION_ID_REQUIRED`, `ROOM_NOT_IN_PROGRESS`, `ROOM_TIME_EXPIRED`, `CONNECTION_REPLACED`)는 [investigation.md](investigation.md#세션-상태에-따른-공통-실패)와 같습니다.

---

## 힌트

### 횟수 규칙 요약

| 항목 | 규칙 |
|---|---|
| 새 힌트 | 난이도별 한도 (쉬움 5 · 보통 3 · 어려움 1). 정상 답변이 저장되어야 1회 사용 확정 |
| 보충 질문 | 힌트마다 최대 1회, 힌트 횟수 차감 없음. 힌트 횟수가 0이어도 가능 |
| 차감 없음 | 창 열기·기록 조회, 직접적인 정답 요구로 차단된 질문, AI 실패 |

### GET /api/rooms/{roomId}/hints

힌트 대화창을 열 때 호출합니다. 질문·답변과 보충 질문·답변을 시간순으로 반환합니다. 모든 상태에서 조회할 수 있습니다.

**성공 응답** `200` `HintListResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `hintCount` | [UsageCount](room.md#usagecount) | |
| `hints` | [HintResponse](#hintresponse)[] | 오래된 순 |
| `pendingRequests` | [AiRequestStatusResponse](room.md#airequeststatusresponse)[] | `PROCESSING`이거나 재시도 대기 중인 `FAILED` 새 힌트·보충 질문 요청 (`type`: `HINT` / `HINT_FOLLOWUP`). 없으면 `[]` |

#### HintResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `hintId` | number | |
| `requestId` | string | |
| `question` | string | 플레이어 질문 |
| `content` | string | AI 답변 |
| `createdAt` | string | |
| `followup` | [HintFollowupResponse](#hintfollowupresponse) \| null | 보충 질문·답변 |
| `followupAvailable` | boolean | 미사용 기회가 남았는지 (`followup == null`이고 처리 중인 보충 질문이 없음). 세션 상태와 무관한 값이며 지금 전송 가능하다는 뜻은 아님 |

보충 질문 전송 버튼은 최신 `followupAvailable=true`이고 `state.timer.status=IN_PROGRESS`, `remainingMs > 0`, 현재 조작 연결을 가진 경우에만 활성화합니다. 서버가 최종 검증합니다.

#### HintFollowupResponse

| 필드 | 타입 |
|---|---|
| `hintId` | number |
| `requestId` | string |
| `question` | string |
| `content` | string |
| `createdAt` | string |

---

### POST /api/rooms/{roomId}/hints

새 힌트를 요청합니다. 전송 전 화면에 남은 횟수와 "전송하면 힌트 1회를 사용합니다"를 표시합니다. 세션당 한 번에 하나만 처리합니다.

**요청 헤더**: `X-Connection-Id` 필수

**요청 본문** `HintCreateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `requestId` | string | O | UUID |
| `question` | string | O | 1~500자 |

**성공 응답** `200` `HintCreateResponse` / 같은 요청이 처리 중이면 `202` [AiRequestStatusResponse](room.md#airequeststatusresponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `hint` | [HintResponse](#hintresponse) | |
| `hintCount` | [UsageCount](room.md#usagecount) | 사용 반영 후 횟수 |

**실패 응답** (공통 실패 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 질문 누락·길이 초과 |
| 400 | `QUESTION_BLOCKED` | 직접적인 정답 요구 (차감 없음) |
| 409 | `HINT_LIMIT_EXCEEDED` | 남은 힌트 없음 (처리 중 확보분 포함) |
| 409 | `AI_REQUEST_ID_REUSED` | 같은 `requestId`에 다른 입력 |
| 409 | `AI_REQUEST_IN_PROGRESS` | 다른 새 힌트가 처리 중 |
| 409 | `ROOM_ENDED_DURING_REQUEST` | 생성 중 세션이 종료되어 반영되지 않음 (차감 없음) |
| 502 | `HINT_GENERATION_FAILED` | Claude API 실패 (차감 없음). 같은 요청으로 재시도 |

---

### POST /api/rooms/{roomId}/hints/{hintId}/followup

기존 힌트의 뜻을 풀어서 설명받는 무료 보충 질문입니다. 새로운 단서·정답 정보는 추가하지 않습니다. 새 정보를 요구하면 AI가 "새 힌트가 필요합니다"라고 안내하며, 이 경우도 보충 질문 1회를 사용한 것으로 처리합니다.

**요청 헤더**: `X-Connection-Id` 필수

**요청 본문** `HintFollowupRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `requestId` | string | O | UUID |
| `question` | string | O | 1~500자 |

**성공 응답** `200` [HintFollowupResponse](#hintfollowupresponse) / 처리 중이면 `202`

**실패 응답** (공통 실패 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | |
| 400 | `QUESTION_BLOCKED` | 직접적인 정답 요구 (사용 처리 없음) |
| 404 | `HINT_NOT_FOUND` | 이 세션의 힌트가 아님 |
| 409 | `HINT_FOLLOWUP_ALREADY_USED` | 이 힌트의 보충 질문을 이미 사용함 |
| 409 | `AI_REQUEST_ID_REUSED` | |
| 409 | `AI_REQUEST_IN_PROGRESS` | 이 힌트의 보충 질문이 처리 중 |
| 409 | `ROOM_ENDED_DURING_REQUEST` | |
| 502 | `HINT_FOLLOWUP_FAILED` | Claude API 실패 (사용 처리 없음) |

---

## 지목

### 규칙 요약

| 항목 | 규칙 |
|---|---|
| 기회 | 정상 채점 완료(`COMPLETED`) 기준 세션당 3회. 페널티 없음 |
| 판정 | 범인 틀림 → 오답 / 범인 맞음 + 필수 항목 전부 충족 → 정답 / 일부 충족 → 부분 정답 / 하나도 없음 → 오답 |
| 종료 | 정답 → `CLEARED`, 3회째도 정답이 아니면 → `FAILED`, 시간 소진 → `EXPIRED` |
| 유효한 답안 | 서버 접수 시점에 누적 진행 시간이 제한시간 미만인 답안. 채점 중 시간이 끝나도 결과 적용 |
| 동시 처리 | 채점 중(`PENDING`)인 지목이 있으면 새 지목 불가 |
| 채점 실패 | 기회 미차감. 같은 `requestId`·같은 답안으로 재채점 (마감 후에도 가능, 최소 10초 간격) |
| 힌트와의 관계 | 지목 피드백은 힌트 횟수를 차감하지 않음 |

### AccusationResponse

지목 1건입니다. 플레이어에게는 난이도별로 구성된 `feedback`만 제공하며, 항목별 판정 결과(`element_results`)는 반환하지 않습니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `accusationId` | number | |
| `requestId` | string | |
| `suspectId` | number | 지목한 인물 |
| `suspectName` | string | |
| `trickExplanation` | string | 제출한 트릭 설명 |
| `submittedAt` | string | 서버 접수 시각 |
| `gradingStatus` | `GradingStatus` | `PENDING` / `COMPLETED` / `ERROR` |
| `verdict` | `Verdict` \| null | `COMPLETED`일 때만 |
| `feedback` | string \| null | `COMPLETED`일 때만. 난이도별 상세도 |
| `attemptNo` | number \| null | 정상 채점 완료된 몇 번째 지목인지 (1~3) |
| `retryAvailableAt` | string \| null | `ERROR`일 때 재채점 가능 시각 |
| `superseded` | boolean | 채점 실패 후 새 답안을 제출해 대체된 지목 (재채점 불가) |

---

### GET /api/rooms/{roomId}/accusations

지목 기록입니다. 이전 피드백을 다시 보거나 재접속 시 복원할 때 사용합니다. 모든 상태에서 조회할 수 있습니다.

**성공 응답** `200` `AccusationListResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `accusationCount` | [UsageCount](room.md#usagecount) | |
| `accusations` | [AccusationResponse](#accusationresponse)[] | 오래된 순 |

---

### POST /api/rooms/{roomId}/accusations

범인과 트릭 설명을 함께 제출하고 AI 채점 결과를 받습니다. 같은 엔드포인트로 **새 답안 제출**과 **실패한 채점의 재시도**를 처리합니다.

| 구분 | 조건 | 연결 헤더 | 세션 조건 |
|---|---|---|---|
| 새 답안 | 새 `requestId` | 필수 | `IN_PROGRESS`, 남은 시간 있음, 남은 기회 있음, 채점 중인 지목 없음 |
| 재채점 | 이전에 `ERROR`가 된 `requestId` + 같은 `suspectId`·`trickExplanation` | 불필요 | 종료되지 않은 세션(`IN_PROGRESS`/`PAUSED`). 마감 후에도 가능. 이전 시도 후 최소 10초 경과 |

- 채점 실패 후 마감 전에 **새 답안**을 제출하면, 이전 실패 지목은 `superseded=true`가 되고 다시 채점할 수 없습니다.
- 마감 후에는 새 답안(답안 변경)을 받지 않습니다(`409 ROOM_TIME_EXPIRED`).

**요청 헤더**: 새 답안이면 `X-Connection-Id` 필수

**요청 본문** `AccusationCreateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `requestId` | string | O | UUID |
| `suspectId` | number | O | 이 사건의 인물 ([맵 API](room.md#get-apiroomsroomidmap)의 `suspects`) |
| `trickExplanation` | string | O | 10~3000자 |

**성공 응답** `200` `AccusationCreateResponse` / 같은 요청이 채점 중이면 `202` [AiRequestStatusResponse](room.md#airequeststatusresponse)

| 필드 | 타입 | 설명 |
|---|---|---|
| `accusation` | [AccusationResponse](#accusationresponse) | `gradingStatus=COMPLETED` |
| `accusationCount` | [UsageCount](room.md#usagecount) | 반영 후 횟수 |
| `roomStatus` | `RoomStatus` | 반영 후 세션 상태. `CLEARED`/`FAILED`/`EXPIRED`면 결과 화면으로 이동 |

**실패 응답** (공통 실패 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 필드 누락·길이 위반 |
| 404 | `SUSPECT_NOT_FOUND` | 이 사건의 인물이 아님 |
| 409 | `ACCUSATION_LIMIT_EXCEEDED` | 남은 지목 기회 없음 |
| 409 | `AI_REQUEST_IN_PROGRESS` | 다른 지목이 채점 중 |
| 409 | `AI_REQUEST_ID_REUSED` | 같은 `requestId`에 다른 답안 |
| 409 | `AI_REQUEST_SUPERSEDED` | 새 답안으로 대체된 지목의 재채점 시도 |
| 429 | `GRADING_RETRY_TOO_SOON` | 재채점 최소 간격 전. `detail: { "retryAvailableAt" }` |
| 502 | `GRADING_FAILED` | 채점 실패 (기회 미차감). `detail: { "requestId", "accusationId", "retryAvailableAt" }` |

```json
// 요청
{
  "requestId": "0b7d2c55-8f3e-4f7a-a1d2-6c1e3b9e4a70",
  "suspectId": 3,
  "trickExplanation": "집사가 녹음기를 사용했다."
}

// 응답 (쉬움 난이도, 부분 정답)
{
  "success": true,
  "message": "지목 채점 성공",
  "data": {
    "accusation": {
      "accusationId": 5,
      "requestId": "0b7d2c55-8f3e-4f7a-a1d2-6c1e3b9e4a70",
      "suspectId": 3,
      "suspectName": "해리스",
      "trickExplanation": "집사가 녹음기를 사용했다.",
      "submittedAt": "2026-10-05T14:20:31",
      "gradingStatus": "COMPLETED",
      "verdict": "PARTIAL",
      "feedback": "녹음기를 짚은 것은 맞아요. 어디에, 어떤 목적으로 사용했을까요? 집사의 시간 관련 진술을 참고해 보세요.",
      "attemptNo": 1,
      "retryAvailableAt": null,
      "superseded": false
    },
    "accusationCount": { "limit": 3, "used": 1, "reserved": 0, "remaining": 2 },
    "roomStatus": "IN_PROGRESS"
  },
  "timestamp": "2026-10-05T14:20:44.918"
}
```
