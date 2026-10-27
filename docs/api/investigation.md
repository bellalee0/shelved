# 수사 API (NPC · 물증 · 개인 노트)

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 2.4절, 3.7~3.8절, 7.3절 기준입니다.

| Method | 경로 | 설명 | 연결 헤더 |
|---|---|---|---|
| `POST` | `/api/rooms/{roomId}/npc/{suspectId}/ask` | NPC에게 자유 질문 (AI) | 필수 |
| `GET` | `/api/rooms/{roomId}/npc/{suspectId}/messages` | NPC별 대화 원문 기록 | |
| `POST` | `/api/rooms/{roomId}/evidences/{evidenceId}/inspect` | 물증 조사 | 필수 |
| `GET` | `/api/rooms/{roomId}/notes/me` | 내 개인 노트 조회 | |
| `PUT` | `/api/rooms/{roomId}/notes/me` | 내 개인 노트 저장 | 필수 |

모든 API는 로그인이 필요합니다. 본인 세션이 아니면 `404 ROOM_NOT_FOUND`입니다.

### 세션 상태에 따른 공통 실패

조작 API(POST·PUT)는 아래 순서로 검사합니다. 조회(GET)는 모든 상태에서 가능합니다.

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `CONNECTION_ID_REQUIRED` | `X-Connection-Id` 헤더 없음 |
| 409 | `ROOM_NOT_IN_PROGRESS` | `READY`·`PAUSED`·종료 상태 |
| 409 | `ROOM_TIME_EXPIRED` | 서버 기준 남은 시간 없음 |
| 409 | `CONNECTION_REPLACED` | 현재 조작 연결이 아님 |

단, 같은 `requestId`로 **이미 완료된** NPC 질문을 다시 보내면 위 검사 없이 저장된 결과를 반환합니다.

---

## POST /api/rooms/{roomId}/npc/{suspectId}/ask

NPC에게 자유 질문을 보내고 답변을 받습니다. 질문과 답변은 NPC 대화 기록에 저장되며 개인 노트에는 반영하지 않습니다.

- `suspectId`가 이 세션 사건의 인물이 아니면 `404 SUSPECT_NOT_FOUND`, 대화할 수 없는 인물(`canConverse=false`)이면 `400 NPC_NOT_CONVERSABLE`입니다.
- 범인 정체·범행 여부를 직접 또는 우회적으로 묻는 질문은 AI 요청 접수 전에 차단하고 `400 QUESTION_BLOCKED`를 반환합니다. 대화 기록에 저장하지 않습니다. `message`에 "행적·목격 사실·물증에 관해 질문해 보세요" 같은 안내가 담깁니다.
- 같은 세션·같은 NPC에는 한 번에 한 질문만 처리합니다. requestId 규칙은 [README](README.md#ai-요청-처리-requestid)를 따릅니다.

**요청 헤더**: `X-Connection-Id` 필수

**요청 본문** `NpcAskRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `requestId` | string | O | UUID |
| `question` | string | O | 1~500자 |

**성공 응답** `200` `NpcAskResponse` / 같은 요청이 처리 중이면 `202` [AiRequestStatusResponse](room.md#airequeststatusresponse)

### NpcAskResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestId` | string | |
| `suspectId` | number | |
| `question` | [NpcMessageResponse](#npcmessageresponse) | 저장된 플레이어 질문 |
| `answer` | [NpcMessageResponse](#npcmessageresponse) | 저장된 NPC 답변 |

### NpcMessageResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageId` | number | |
| `senderType` | `MessageSenderType` | `USER` / `NPC` |
| `content` | string | |
| `sequenceNo` | number | 이 NPC 대화 안의 순번 (정렬 기준) |
| `createdAt` | string | |

**실패 응답** (공통 실패 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 질문 누락·길이 초과, `requestId` 형식 오류 |
| 400 | `QUESTION_BLOCKED` | 직접적인 정답 요구 |
| 400 | `NPC_NOT_CONVERSABLE` | 대화할 수 없는 인물 |
| 404 | `SUSPECT_NOT_FOUND` | 이 사건의 인물이 아님 |
| 409 | `AI_REQUEST_ID_REUSED` | 같은 `requestId`에 다른 입력 |
| 409 | `AI_REQUEST_IN_PROGRESS` | 이 NPC에게 다른 질문이 처리 중 |
| 409 | `ROOM_ENDED_DURING_REQUEST` | 답변 생성 중 세션이 종료되어 반영되지 않음 |
| 502 | `NPC_ANSWER_FAILED` | Claude API 실패. 같은 `requestId`·질문으로 재시도 |

```json
// 요청
{ "requestId": "5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11", "question": "어젯밤 9시에 서재 근처에 있었나요?" }

// 응답
{
  "success": true,
  "message": "NPC 질문 성공",
  "data": {
    "requestId": "5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11",
    "suspectId": 3,
    "question": { "messageId": 41, "senderType": "USER", "content": "어젯밤 9시에 서재 근처에 있었나요?", "sequenceNo": 7, "createdAt": "2026-10-05T14:07:10" },
    "answer": { "messageId": 42, "senderType": "NPC", "content": "그 시간이면 주방에서 은식기를 닦고 있었습니다. 서재에서 주인님 목소리를 들은 건 기억납니다만.", "sequenceNo": 8, "createdAt": "2026-10-05T14:07:18" }
  },
  "timestamp": "2026-10-05T14:07:18.402"
}
```

---

## GET /api/rooms/{roomId}/npc/{suspectId}/messages

한 NPC와의 질문·답변 원문을 `sequenceNo` 순으로 반환합니다. 진위·중요도 표시나 요약은 없습니다. 모든 상태에서 조회할 수 있습니다.

**성공 응답** `200` `NpcMessageListResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `suspectId` | number | |
| `suspectName` | string | |
| `messages` | [NpcMessageResponse](#npcmessageresponse)[] | |
| `pendingRequests` | [AiRequestStatusResponse](room.md#airequeststatusresponse)[] | 이 NPC에게 보낸 질문 중 `PROCESSING`이거나 재시도 대기 중인 `FAILED` 요청. 대화창에서 "답변 생성 중"·"다시 보내기"를 표시한다. 없으면 `[]` |

**실패 응답**: `404 SUSPECT_NOT_FOUND`

---

## POST /api/rooms/{roomId}/evidences/{evidenceId}/inspect

물증을 조사해 검수된 설명을 받습니다. 같은 물증을 몇 번이든 다시 조사할 수 있으며 항상 같은 설명을 반환합니다. 서버는 최초 조사 시각만 내부 이력으로 기록합니다(힌트 참고용, 플레이어에게 목록으로 제공하지 않음).

거리 검사는 클라이언트(Phaser)가 담당하며 서버는 이동 경로를 검증하지 않습니다.

**요청 헤더**: `X-Connection-Id` 필수

**요청 본문**: 없음

**성공 응답** `200` `EvidenceInspectResponse`

| 필드 | 타입 |
|---|---|
| `evidenceId` | number |
| `name` | string |
| `description` | string |

**실패 응답** (공통 실패 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 404 | `EVIDENCE_NOT_FOUND` | 이 사건의 물증이 아님 |

---

## GET /api/rooms/{roomId}/notes/me

내 개인 노트입니다. 세션 생성 시 빈 노트(`revision=0`)가 함께 만들어집니다. 일시정지·종료 후에도 조회할 수 있습니다.

**성공 응답** `200` `NoteResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `content` | string | 플레이어가 직접 작성한 텍스트 |
| `revision` | number | 저장할 때 함께 보낼 버전 |
| `updatedAt` | string | |
| `maxLength` | number | 최대 글자 수 (서버 설정값) |
| `editable` | boolean | 지금 편집 가능한지 (`IN_PROGRESS`이고 시간이 남아 있을 때만 `true`) |

---

## PUT /api/rooms/{roomId}/notes/me

노트 전체 내용을 저장합니다. 요청의 `revision`이 서버의 현재 값과 같을 때만 저장하고 `revision`을 1 올립니다. 다르면 덮어쓰지 않고 `409 NOTE_REVISION_CONFLICT`와 서버 내용을 반환합니다.

- 자동 저장은 한 번에 하나씩 보냅니다. 응답의 새 `revision`으로 다음 저장을 보냅니다.
- 저장이 거부되면(충돌, 일시정지, 시간 종료, 연결 교체) 프론트엔드는 입력 내용을 브라우저에 "이 기기의 미저장 초안"으로 보존합니다.

**요청 헤더**: `X-Connection-Id` 필수

**요청 본문** `NoteUpdateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `content` | string | O | 0~`maxLength`자 (빈 문자열 허용) |
| `revision` | number | O | 마지막으로 받은 `revision` |

**성공 응답** `200` [NoteResponse](#get-apiroomsroomidnotesme)

**실패 응답** (공통 실패 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 글자 수 초과 |
| 409 | `NOTE_REVISION_CONFLICT` | `revision` 불일치. `detail: { "currentRevision", "currentContent" }` |
