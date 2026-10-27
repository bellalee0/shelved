# 플레이 세션 API

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 3.1절, 3.5~3.6절, 3.8절, 6.4절, 7.3절 기준입니다.
> MVP는 솔로 전용이며 세션(방) 참가자는 생성한 사용자 1명입니다.

| Method | 경로 | 설명 | 연결 헤더 |
|---|---|---|---|
| `GET` | `/api/rooms` | 내 세션 목록 (이어하기·결과 보기 진입) | |
| `POST` | `/api/rooms` | 세션 생성 (`READY`) | |
| `GET` | `/api/rooms/{roomId}/state` | 세션 상태·시간·남은 횟수·처리 중 요청 (재접속 복원) | |
| `GET` | `/api/rooms/{roomId}/briefing` | 사전 정보 | |
| `GET` | `/api/rooms/{roomId}/map` | 맵 설정과 NPC·물증 배치 | |
| `POST` | `/api/rooms/{roomId}/start` | 수사 시작 (`READY → IN_PROGRESS`), 연결 식별자 발급 | |
| `POST` | `/api/rooms/{roomId}/heartbeat` | 접속 확인, 서버 기준 시간 반환 | 필수 |
| `POST` | `/api/rooms/{roomId}/pause` | 일시정지 (`IN_PROGRESS → PAUSED`) | |
| `POST` | `/api/rooms/{roomId}/resume` | 수사 이어하기 (`PAUSED → IN_PROGRESS`), 새 연결 식별자 발급 | |
| `GET` | `/api/rooms/{roomId}/result` | 종료 세션의 리빌·플레이 기록 | |
| `GET` | `/api/rooms/{roomId}/ai-requests/{requestId}` | AI 요청 처리 상태·저장된 결과 | |

모든 API는 로그인이 필요합니다. `{roomId}`가 존재하지 않거나 **본인 세션이 아니면** `404 ROOM_NOT_FOUND`입니다.

---

## 입장·재접속 흐름 (프론트엔드 기준)

```plain text
[최초 입장]
POST /api/rooms → roomId
GET state, briefing, map (병렬) → 맵 로딩 → PLACE_AT_SPAWN → MAP_READY
"수사 시작" 활성화 → POST start → connectionId 보관 → 하트비트 시작

[재접속·새로고침]
POST pause (헤더 없이)          # 기존 연결의 시간이 계속 흐르지 않도록
GET state, notes/me, hints, accusations, map (병렬) → 처리 중·실패 요청 복원
맵 로딩 → PLACE_AT_SPAWN → MAP_READY
"수사 이어하기" 활성화 → POST resume → 새 connectionId → 하트비트 시작
  ※ state.timer.awaitingGrading=true면 이어하기 대신 "채점 대기" 화면을 표시
     (resume은 409 ROOM_TIME_EXPIRED). 재채점은 연결 헤더 없이 POST accusations

[다른 화면에서 이어하기 (409 CONNECTION_REPLACED)]
조작·하트비트 중단 → "이 화면에서 계속하기"를 누르면 재접속 흐름과 같음

[저장 후 나가기]
PUT notes/me 저장 완료 확인 → POST pause → 메인 화면
```

NPC별 대화 기록은 대화창을 열 때 `GET /npc/{suspectId}/messages`로 불러옵니다.

---

## 공통 응답 객체

### RoomTimerResponse

시간 관련 API가 공통으로 반환합니다. 화면의 타이머는 이 값을 받을 때마다 다시 맞춥니다. 만료 판정은 서버만 합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | `RoomStatus` | 현재 세션 상태 |
| `timeLimitSec` | number | 제한시간 |
| `elapsedActiveMs` | number | 일시정지를 제외한 누적 진행 시간 |
| `remainingMs` | number | 남은 시간 (0 이상) |
| `awaitingGrading` | boolean | 시간은 소진됐지만 마감 전에 접수한 지목의 채점이 끝나지 않아 최종 결과를 보류 중 |
| `serverTime` | string | 이 값을 계산한 서버 시각 |

### AiRequestStatusResponse

AI 요청의 처리 상태입니다. AI POST의 `202` 응답, 상태 조회 API, 세션 복원 응답에서 사용합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestId` | string | |
| `type` | `AiRequestType` | `NPC_ASK` / `HINT` / `HINT_FOLLOWUP` / `ACCUSATION` |
| `targetId` | number \| null | `NPC_ASK`: suspectId, `HINT_FOLLOWUP`: hintId, 그 외 `null` |
| `status` | `AiRequestStatus` | `PROCESSING` / `SUCCEEDED` / `FAILED` |
| `input` | object | 플레이어가 보낸 입력. 재시도 시 그대로 다시 보낸다. `NPC_ASK`·`HINT`·`HINT_FOLLOWUP`: `{ "question" }`, `ACCUSATION`: `{ "suspectId", "trickExplanation" }` |
| `result` | object \| null | `SUCCEEDED`일 때 원래 POST의 성공 응답과 같은 객체 (`NPC_ASK`: [NpcAskResponse](investigation.md#npcaskresponse), `HINT`: [HintCreateResponse](hint-accusation.md#post-apiroomsroomidhints), `HINT_FOLLOWUP`: [HintFollowupResponse](hint-accusation.md#hintfollowupresponse), `ACCUSATION`: [AccusationCreateResponse](hint-accusation.md#post-apiroomsroomidaccusations)) |
| `error` | object \| null | `FAILED`일 때 `{ "code", "message" }` |
| `retryAvailableAt` | string \| null | `ACCUSATION`이 `FAILED`일 때 같은 답안 재채점이 가능한 시각. 그 외 `null` |
| `acceptedAt` | string | 서버 최초 접수 시각 |
| `updatedAt` | string | |

AI 내부 입력 컨텍스트(`input_json`의 프롬프트 등)는 포함하지 않습니다. `input`에는 플레이어가 직접 입력한 값만 담습니다.

---

## GET /api/rooms

메인 화면의 "이어하기 / 결과 보기" 목록입니다. 관리자 테스트 세션은 포함하지 않습니다. 최신 생성 순입니다.

- `pendingGradingStatus=ERROR`인 세션의 "다시 채점하기"는 이 목록에서 바로 재채점하지 않고 게임 화면으로 이동합니다. 게임 화면이 `state.pendingAccusation`의 `requestId`·답안으로 [재채점](hint-accusation.md#post-apiroomsroomidaccusations)합니다.

**쿼리 파라미터**

| 이름 | 타입 | 필수 | 설명 |
|---|---|---|---|
| `status` | `ACTIVE` \| `ENDED` | X | `ACTIVE`: `READY`/`IN_PROGRESS`/`PAUSED`, `ENDED`: `CLEARED`/`FAILED`/`EXPIRED`. 생략 시 전체 |
| `page`, `size` | number | X | |

**성공 응답** `200` PageResponse&lt;`RoomSummaryResponse`&gt;

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | number | |
| `caseId` | number | |
| `displayTitle` | string | |
| `difficulty` | `Difficulty` | |
| `coverImageUrl` | string \| null | |
| `status` | `RoomStatus` | |
| `remainingMs` | number | 종료 세션은 `0` |
| `pendingGradingStatus` | `PENDING` \| `ERROR` \| null | 채점 중·채점 실패 지목이 있으면 표시 ("채점 대기/실패") |
| `endedAt` | string \| null | 최종 종료 시각. 종료 전 `null` |
| `durationSec` | number \| null | 기록된 진행 시간 ([결과 API](#get-apiroomsroomidresult)의 `durationSec`와 같음). 종료 전 `null` |
| `createdAt` | string | |

---

## POST /api/rooms

사건 카드를 선택하면 `READY` 세션을 만듭니다. 난이도·제한시간은 사건에서 가져옵니다.

- `PUBLISHED` 사건만 가능합니다. 비공개·존재하지 않는 사건은 `404 CASE_NOT_FOUND`.
- **같은 사건에 종료되지 않은 세션(`READY`/`IN_PROGRESS`/`PAUSED`)이 이미 있으면 새로 만들지 않고** `409 ROOM_ALREADY_EXISTS`와 기존 `roomId`를 반환합니다. 프론트엔드는 "진행 중인 수사가 있습니다 → 이어하기"로 안내합니다. 다른 사건의 세션은 영향을 주지 않습니다.

**요청 본문** `RoomCreateRequest`

| 필드 | 타입 | 필수 |
|---|---|---|
| `caseId` | number | O |

**성공 응답** `201` `RoomCreateResponse`

| 필드 | 타입 |
|---|---|
| `roomId` | number |
| `caseId` | number |
| `status` | `RoomStatus` (`READY`) |

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 404 | `CASE_NOT_FOUND` | 사건 없음 또는 비공개 |
| 409 | `ROOM_ALREADY_EXISTS` | 같은 사건의 미종료 세션 존재. `detail: { "roomId": 12 }` |

---

## GET /api/rooms/{roomId}/state

세션의 현재 상태입니다. 입장·재접속 시 가장 먼저 호출합니다. 모든 상태에서 조회할 수 있습니다.

**성공 응답** `200` `RoomStateResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | number | |
| `caseId` | number | |
| `displayTitle` | string | |
| `difficulty` | `Difficulty` | |
| `isTest` | boolean | 관리자 테스트 세션 여부 |
| `startedAt` | string \| null | 최초 "수사 시작" 시각. `READY`면 `null` |
| `timer` | [RoomTimerResponse](#roomtimerresponse) | |
| `heartbeatIntervalMs` | number | 하트비트 전송 간격 (서버 설정값, 잠정 5000) |
| `hintCount` | [UsageCount](#usagecount) | 새 힌트 횟수 |
| `accusationCount` | [UsageCount](#usagecount) | 지목 횟수 (정상 채점 완료 기준) |
| `pendingAccusation` | object \| null | 채점 중(`PENDING`)이거나 채점 실패(`ERROR`)한 지목. 형식은 [AccusationResponse](hint-accusation.md#accusationresponse) |
| `aiRequests` | [AiRequestStatusResponse](#airequeststatusresponse)[] | `PROCESSING`이거나 재시도 대기 중인 `FAILED` 요청 (대체된 지목 제외) |
| `conversations` | object[] | 대화한 NPC 목록 `{ "suspectId", "messageCount", "lastMessageAt" }` |

### UsageCount

| 필드 | 타입 | 설명 |
|---|---|---|
| `limit` | number | 한도 |
| `used` | number | 사용 확정 |
| `reserved` | number | 처리 중 임시 확보 (0 또는 1) |
| `remaining` | number | `limit - used - reserved` |

화면에는 "남은 횟수 `remaining` (처리 중 `reserved`)"처럼 사용 완료와 처리 중을 구분해 표시합니다.

```json
{
  "success": true,
  "message": "세션 상태 조회 성공",
  "data": {
    "roomId": 12,
    "caseId": 1,
    "displayTitle": "시계탑 저택의 마지막 종소리",
    "difficulty": "EASY",
    "isTest": false,
    "startedAt": "2026-10-05T14:00:02",
    "timer": {
      "status": "PAUSED",
      "timeLimitSec": 1200,
      "elapsedActiveMs": 431250,
      "remainingMs": 768750,
      "awaitingGrading": false,
      "serverTime": "2026-10-05T14:30:00"
    },
    "heartbeatIntervalMs": 5000,
    "hintCount": { "limit": 5, "used": 2, "reserved": 0, "remaining": 3 },
    "accusationCount": { "limit": 3, "used": 1, "reserved": 0, "remaining": 2 },
    "pendingAccusation": null,
    "aiRequests": [
      {
        "requestId": "5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11",
        "type": "NPC_ASK",
        "targetId": 3,
        "status": "FAILED",
        "input": { "question": "어젯밤 9시에 서재 근처에 있었나요?" },
        "result": null,
        "error": { "code": "NPC_ANSWER_FAILED", "message": "NPC의 답변을 받지 못했습니다. 같은 질문을 다시 보내 주세요." },
        "retryAvailableAt": null,
        "acceptedAt": "2026-10-05T14:07:10",
        "updatedAt": "2026-10-05T14:07:40"
      }
    ],
    "conversations": [
      { "suspectId": 3, "messageCount": 6, "lastMessageAt": "2026-10-05T14:06:55" }
    ]
  },
  "timestamp": "2026-10-05T14:30:00.123"
}
```

---

## GET /api/rooms/{roomId}/briefing

시작 화면과 게임 중 "정보" 버튼에서 보여줄 사전 정보입니다. 모든 상태에서 조회할 수 있고, 조회해도 타이머는 멈추지 않습니다. 원작 제목·범인·트릭·내부 용어집은 포함하지 않습니다.

**성공 응답** `200` `RoomBriefingResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `displayTitle` | string | |
| `difficulty` | `Difficulty` | |
| `introduction` | string | 사건 소개 |
| `investigationGoal` | string | 수사 목표 |
| `backgroundKnowledge` | string | 기본 배경지식 |
| `timeLimitSec` | number | |
| `hintLimit` | number | |
| `accusationLimit` | number | `3` |

---

## GET /api/rooms/{roomId}/map

Phaser가 맵을 그리는 데 필요한 정보입니다. 모든 상태에서 조회할 수 있습니다.

- 물증 설명, 범인 여부, NPC 내부 설정은 포함하지 않습니다. 물증 설명은 [조사 API](investigation.md#post-apiroomsroomidevidencesevidenceidinspect) 응답으로만 받습니다.
- `suspects`는 **지목 대상 목록으로도 사용**합니다. 맵에 배치되지 않은 인물(`x`, `y`가 `null`)도 지목 대상에 포함됩니다.
- 좌표는 맵 좌상단 원점의 월드 픽셀 단위입니다.

**성공 응답** `200` `RoomMapResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `mapUrl` | string | 정적 에셋 경로 (예: `/assets/maps/clocktower-mansion.json`) |
| `spawnX`, `spawnY` | number | 시작 위치 |
| `collisionLayer` | string | 충돌 판정 레이어 이름 |
| `interactionRadiusPx` | number | 상호작용 반경 |
| `suspects` | object[] | `{ "suspectId", "name", "spriteKey", "canConverse", "x", "y" }` — 배치되지 않았으면 `x`, `y`는 `null` |
| `evidences` | object[] | `{ "evidenceId", "name", "x", "y" }` |

Phaser의 `INTERACT_REQUESTED { type, targetId }`에서 `targetId`는 `suspectId` 또는 `evidenceId`입니다.

---

## POST /api/rooms/{roomId}/start

맵 로딩이 끝난 뒤 "수사 시작"을 누르면 호출합니다. `READY`에서만 가능합니다. 서버 타이머를 시작하고 조작 연결 식별자를 발급합니다.

**요청 본문**: 없음

**성공 응답** `200` `RoomConnectionResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `connectionId` | string | 이후 조작 API의 `X-Connection-Id` 헤더 값 |
| `heartbeatIntervalMs` | number | |
| `timer` | [RoomTimerResponse](#roomtimerresponse) | |

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `ROOM_NOT_READY` | `READY`가 아님 (이미 시작됨 → 상태 조회 후 이어하기로 안내) |

---

## POST /api/rooms/{roomId}/heartbeat

`IN_PROGRESS` 동안 `heartbeatIntervalMs`마다 보냅니다. 창이 열려 있거나 AI 응답을 기다리는 중에도 멈추지 않습니다.

- 서버는 `last_seen_at`을 갱신하고 현재 시간을 반환합니다.
- **일시정지 상태를 자동 재개하지 않습니다.** 늦게 도착한 하트비트가 `PAUSED` 세션에 오면 상태만 반환합니다.
- 세션이 `IN_PROGRESS`가 아니면 연결 검사 없이 현재 상태를 `200`으로 반환합니다. 프론트엔드는 `timer.status`를 보고 `PAUSED`면 이어하기 화면, 종료 상태면 결과 화면으로 이동합니다.

**요청 헤더**: `X-Connection-Id` 필수

**요청 본문**: 없음

**성공 응답** `200` [RoomTimerResponse](#roomtimerresponse)

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `CONNECTION_ID_REQUIRED` | 헤더 없음 |
| 409 | `CONNECTION_REPLACED` | 다른 화면에서 이어하기 실행됨 |

---

## POST /api/rooms/{roomId}/pause

"저장 후 나가기"와 재접속·새로고침 복원 시 호출합니다. 누적 진행 시간을 확정하고 현재 연결을 무효화합니다.

- `IN_PROGRESS`일 때만 상태가 바뀝니다. 그 외 상태에서는 아무것도 바꾸지 않고 현재 상태를 `200`으로 반환합니다(중복 호출해도 안전).
- 새로고침 직후 이전 `connectionId`가 없어도 호출할 수 있도록 연결 헤더를 요구하지 않습니다.
- 이미 접수된 AI 요청은 취소하지 않고 결과를 저장합니다.

**요청 본문**: 없음

**성공 응답** `200` [RoomTimerResponse](#roomtimerresponse)

---

## POST /api/rooms/{roomId}/resume

"수사 이어하기"입니다. `PAUSED`에서만 가능합니다. 새 `connectionId`를 발급하며 이전 연결의 하트비트·조작 요청은 이후 `409 CONNECTION_REPLACED`가 됩니다.

**요청 본문**: 없음

**성공 응답** `200` `RoomConnectionResponse` ([start](#post-apiroomsroomidstart)와 같은 형식)

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `ROOM_NOT_PAUSED` | `PAUSED`가 아님 |
| 409 | `ROOM_TIME_EXPIRED` | 남은 시간이 없음 (채점 대기 중인 세션) |

---

## GET /api/rooms/{roomId}/result

최종 종료(`CLEARED`/`FAILED`/`EXPIRED`)된 세션의 리빌 화면 데이터입니다. **종료 전에는 정답 정보를 반환하지 않고** `409 ROOM_NOT_ENDED`입니다. 종료 후에는 언제든 다시 조회할 수 있습니다.

**성공 응답** `200` `RoomResultResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | number | |
| `caseId` | number | |
| `displayTitle` | string | |
| `isTest` | boolean | |
| `status` | `RoomStatus` | `CLEARED` 정답 / `FAILED` 지목 3회 소진 / `EXPIRED` 시간 만료 |
| `endedAt` | string | |
| `durationSec` | number | `CLEARED`: 정답 접수 시점까지 누적 진행 시간, `FAILED`: 종료 시점까지, `EXPIRED`: 제한시간 |
| `hintUsedCount` | number | |
| `accusationCompletedCount` | number | 정상 채점 완료된 지목 수 |
| `source` | object | `{ "novelTitle", "novelAuthor", "novelIntro" }` 원작 공개. `novelIntro`는 원작 소개 문장 |
| `culprit` | object | `{ "suspectId", "name" }` |
| `trickSummary` | string | 트릭 요약 |
| `revealExplanation` | string | 리빌 해설 (제작 시 검수된 고정 텍스트) |
| `accusations` | [AccusationResponse](hint-accusation.md#accusationresponse)[] | 이 세션의 지목 기록 |

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `ROOM_NOT_ENDED` | 종료되지 않은 세션 |

---

## GET /api/rooms/{roomId}/ai-requests/{requestId}

AI POST가 `202`를 받았거나, 응답을 받지 못한 채 연결이 끊긴 경우 결과를 확인합니다. 일시정지·종료 후에도 조회할 수 있습니다. 프론트엔드는 `PROCESSING`인 동안 2초 간격으로 조회합니다.

**성공 응답** `200` [AiRequestStatusResponse](#airequeststatusresponse)

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 404 | `AI_REQUEST_NOT_FOUND` | 이 세션에 해당 `requestId`가 없음 |
