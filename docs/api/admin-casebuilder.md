# 관리자 사건 제작 API

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 4.1~4.6절, 6.3절, 7.4절 기준입니다.
> 초안(`draft`) 구조와 AI tool은 [admin-draft-schema.md](admin-draft-schema.md)에 있습니다.
> 모든 API는 `role=ADMIN`만 호출할 수 있습니다. 아니면 `403 ACCESS_DENIED`입니다.

| Method | 경로 | 설명 | 허용 사건 상태 |
|---|---|---|---|
| `GET` | `/api/admin/cases` | 관리자 사건 목록 | 전체 |
| `GET` | `/api/admin/assets` | 맵 파일·레이어·spriteKey 목록 (선택 목록용) | — |
| `POST` | `/api/admin/cases` | 새 사건 초안 생성 | — |
| `GET` | `/api/admin/cases/{caseId}` | 제작 상태·초안·콘텐츠·맵·배치 복원 | 전체 |
| `GET` | `/api/admin/cases/{caseId}/messages` | 제작 채팅 이력 | 전체 |
| `POST` | `/api/admin/cases/{caseId}/source-text` | 원문 `.txt` 업로드·교체 | `DRAFT` |
| `POST` | `/api/admin/cases/{caseId}/cover-image` | 표지 이미지 업로드 | `DRAFT`, `CONTENT_CONFIRMED` |
| `POST` | `/api/admin/cases/{caseId}/chat` | 제작 채팅 (AI + tool로 초안 갱신) | `DRAFT` |
| `POST` | `/api/admin/cases/{caseId}/confirm` | 콘텐츠 확정 | `DRAFT` |
| `POST` | `/api/admin/cases/{caseId}/reopen` | 수정 재개 | `CONTENT_CONFIRMED` |
| `PUT` | `/api/admin/cases/{caseId}/map` | 맵 설정 저장 | `CONTENT_CONFIRMED` |
| `PUT` | `/api/admin/cases/{caseId}/hotspots` | NPC·물증 좌표 저장 | `CONTENT_CONFIRMED` |
| `GET` | `/api/admin/cases/{caseId}/test-rooms` | 테스트 세션 목록 | 전체 |
| `POST` | `/api/admin/cases/{caseId}/test-rooms` | 테스트 세션 생성 | `CONTENT_CONFIRMED` |
| `DELETE` | `/api/admin/cases/{caseId}/test-rooms/{roomId}` | 미종료 테스트 중단·삭제 | `CONTENT_CONFIRMED` |
| `POST` | `/api/admin/cases/{caseId}/test-verification` | 현재 내용의 테스트 완료 확인 | `CONTENT_CONFIRMED` |
| `GET` | `/api/admin/cases/{caseId}/publish-check` | 출시 검사 미리보기 | `CONTENT_CONFIRMED` |
| `POST` | `/api/admin/cases/{caseId}/publish` | 출시 | `CONTENT_CONFIRMED` |
| `POST` | `/api/admin/cases/{caseId}/clone` | 공개 사건을 새 초안으로 복제 | `PUBLISHED` |

### 상태·잠금 공통 실패

| 상태 | 코드 | 상황 |
|---|---|---|
| 404 | `CASE_NOT_FOUND` | 사건 없음 |
| 409 | `CASE_PUBLISHED_LOCKED` | 공개된 사건을 수정하려 함 (모든 수정 API 공통, 복제만 허용) |
| 409 | `CASE_NOT_DRAFT` | `DRAFT`에서만 가능한 요청 |
| 409 | `CASE_NOT_CONTENT_CONFIRMED` | `CONTENT_CONFIRMED`에서만 가능한 요청 |
| 409 | `CASE_CHAT_IN_PROGRESS` | 제작 채팅이 처리 중 (채팅·원문 교체·확정 차단) |
| 409 | `TEST_ROOM_UNFINISHED` | 미종료 테스트 세션(`READY`/`IN_PROGRESS`/`PAUSED`)이 있음 (수정 재개·맵·좌표 변경·출시 차단) |
| 409 | `CASE_AI_REQUEST_IN_PROGRESS` | 이 사건의 테스트 세션 AI 요청이 처리 중 (수정 재개·맵·좌표 변경 보류) |

### 테스트 완료 확인이 초기화되는 경우

출시하려면 **현재 내용**으로 테스트를 마치고 "테스트 완료 확인"을 해야 합니다(기획서 4.4절). 아래 변경이 일어나면 서버가 `CASES.content_updated_at`을 갱신하고 `CASES.test_verified_at`을 `null`로 되돌립니다.

- 콘텐츠 확정(재확정 포함), 수정 재개
- 맵 설정 저장, 좌표 저장

표지 이미지 변경은 게임 진행에 영향이 없으므로 초기화하지 않습니다.

---

## 조회

### GET /api/admin/cases

**쿼리 파라미터**: `status` (`CaseStatus`, 선택)

**성공 응답** `200` `List<AdminCaseSummaryResponse>` — 최근 수정 순

| 필드 | 타입 | 설명 |
|---|---|---|
| `caseId` | number | |
| `status` | `CaseStatus` | |
| `displayTitle` | string \| null | 확정 콘텐츠 값, 없으면 초안 값 |
| `sourceNovelTitle` | string \| null | 확정 콘텐츠 값, 없으면 초안 값 |
| `difficulty` | `Difficulty` \| null | |
| `hasSourceText` | boolean | |
| `unfinishedTestRoomCount` | number | |
| `testVerified` | boolean | 현재 내용의 테스트 완료 확인 여부 |
| `updatedAt` | string | |

### GET /api/admin/cases/{caseId}

관리자 화면에서 기존 사건을 열 때 작업 상태를 복원합니다. 채팅 이력은 [messages](#get-apiadmincasescaseidmessages)로 따로 조회합니다.

**성공 응답** `200` `AdminCaseDetailResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `caseId` | number | |
| `status` | `CaseStatus` | |
| `clonedFromCaseId` | number \| null | 복제로 만든 사건이면 원본 ID |
| `coverImageUrl` | string \| null | |
| `sourceText` | SourceTextInfo \| null | 아래 구조. 원문 내용은 포함하지 않음 |
| `draft` | Draft | [초안 구조](admin-draft-schema.md#전체-구조) |
| `draftUpdatedAt` | string | |
| `draftIssues` | [DraftIssue](admin-draft-schema.md#콘텐츠-확정-시-검증과-반영)[] | 현재 초안의 확정 전 검사 결과. 문제가 없으면 `[]` |
| `content` | object \| null | 확정된 콘텐츠의 ID 목록. 확정 전 `null`. 아래 표 |
| `map` | [AdminCaseMapResponse](#put-apiadmincasescaseidmap) \| null | |
| `hotspots` | [HotspotResponse](#put-apiadmincasescaseidhotspots)[] | |
| `contentUpdatedAt` | string \| null | 마지막 콘텐츠·맵·배치 변경 시각 |
| `testVerifiedAt` | string \| null | 현재 내용의 테스트 완료 확인 시각 |
| `chatProcessing` | boolean | 처리 중인 제작 채팅이 있는지 |
| `chatContextRevision` | number | 0 이상의 정수. 원문·초안·제작 상태 변경을 구분하는 서버 버전 |

`content` — 맵 배치 화면에서 사용할 확정 엔티티 목록

| 필드 | 타입 |
|---|---|
| `suspects` | `{ suspectId: number, key: string, name: string, spriteKey: string \| null, canConverse: boolean, isCulprit: boolean }[]` |
| `evidences` | `{ evidenceId: number, key: string, name: string }[]` |
| `trickElements` | `{ trickElementId: number, key: string, orderIndex: number, description: string }[]` |

`orderIndex`는 1 이상의 정수입니다. `spriteKey`는 미배치 인물일 때 `null`을 허용하며, 배치된 인물은 출시 검사에서 유효한 스프라이트를 요구합니다.

#### SourceTextInfo

| 필드 | 타입 | 제약 |
|---|---|---|
| `originalFilename` | string | 업로드 원본 파일명 |
| `charCount` | number | 1 이상의 정수. UTF-16 코드 단위 길이 |
| `uploadedAt` | string | 서버 업로드 시각 |

### GET /api/admin/cases/{caseId}/messages

제작 채팅 이력을 오래된 순으로 반환합니다. 한 요청의 관리자 발화와 AI 발화는 같은 `requestId`로 묶입니다.

**성공 응답** `200` `List<AdminCaseMessageResponse>`

| 필드 | 타입 | 설명 |
|---|---|---|
| `messageId` | number | |
| `requestId` | string | |
| `senderType` | `AdminMessageSenderType` | `ADMIN` / `AI` |
| `content` | string | |
| `toolChanges` | [ToolChange](admin-draft-schema.md#관리자-화면에-보여줄-변경-요약-toolchange)[] | AI 발화의 초안 변경 요약. 관리자 발화는 `[]` |
| `inputInfo` | [InputInfo](#inputinfo) \| null | 이 답변을 만들 때 AI 입력을 어떻게 구성했는지. AI 발화에만 있고 관리자 발화는 `null` |
| `status` | `AiRequestStatus` | 요청 처리 상태. `FAILED`면 AI 발화 없이 관리자 발화만 있음 |
| `errorMessage` | string \| null | `FAILED`일 때 사유 |
| `createdAt` | string | |

---

## 사건 생성 · 파일 업로드

### POST /api/admin/cases

빈 초안을 가진 `DRAFT` 사건을 만듭니다. 요청 본문은 없습니다.

**성공 응답** `201` `{ "caseId": number, "status": "DRAFT" }`

### POST /api/admin/cases/{caseId}/source-text

원작 원문 `.txt`를 업로드합니다. 이미 있으면 교체합니다. DB에 전문을 그대로 저장하며 잘라내지 않습니다.

**요청**: `multipart/form-data`

| 파트 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `file` | file | O | 확장자 `.txt`, UTF-8, 크기 한도는 설정값 (`shelved.casebuilder.source-text-max-size`) |

**성공 응답** `200` [SourceTextInfo](#sourcetextinfo)

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `SOURCE_TEXT_INVALID_FILE` | `.txt`가 아니거나 UTF-8로 읽을 수 없음, 빈 파일 |
| 400 | `FILE_TOO_LARGE` | 크기 한도 초과 |

### POST /api/admin/cases/{caseId}/cover-image

사건 카드 표지를 업로드합니다. 서버 로컬 디스크에 저장하고 `CASES.cover_image_url`을 갱신합니다. 이전 파일은 삭제합니다(복제된 사건과 공유 중이면 유지).

**요청**: `multipart/form-data`

| 파트 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `file` | file | O | `jpg`/`jpeg`/`png`/`webp`, 5MB 이하 (설정값) |

**성공 응답** `200` `{ "coverImageUrl": "/uploads/covers/{파일명}" }`

- 표지 파일은 백엔드가 정적 경로 `GET /uploads/covers/{파일명}`으로 인증 없이 제공합니다. 파일명은 추측할 수 없도록 무작위 문자열을 포함합니다.

**실패 응답** (공통 외): `400 COVER_IMAGE_INVALID_FILE`, `400 FILE_TOO_LARGE`, `500 FILE_STORAGE_FAILED`

---

## 제작 채팅 · 확정

### POST /api/admin/cases/{caseId}/chat

관리자 메시지를 보내면 AI가 답변하고 tool로 초안을 갱신합니다. 사건당 한 번에 한 요청만 처리합니다. 요청 식별의 공통 원칙은 [README](README.md#ai-요청-처리-requestid)를 따르며, 사건 범위와 아래 재시도 예외를 적용합니다.

- 원문이 업로드되어 있어야 합니다(`409 SOURCE_TEXT_REQUIRED`).
- 서버는 원문(또는 필요한 분할 구간)·대화 이력·현재 초안·용어집·tool 정의로 입력을 구성합니다. 모델 입력 한도를 넘으면 오래된 대화 이력을 요약하고, 그래도 넘으면 원문을 분할합니다(기획서 8.4절). **어떻게 구성했는지 `inputInfo`로 알려줍니다.** 그래도 맞출 수 없으면 원문을 조용히 자르지 않고 `400 LLM_INPUT_TOO_LARGE`를 반환합니다.
- AI 호출이 실패하면 초안을 바꾸지 않고 `502 CASE_CHAT_FAILED`를 반환합니다. 같은 `requestId`·메시지로 재시도합니다.

#### 제작 상태 변경과 재시도

- 서버는 사건 생성 시 `chatContextRevision=0`으로 시작하고, 원문 업로드·교체, 새 제작 채팅의 성공 저장(초안 변경이 없어도 대화 이력이 바뀜), 콘텐츠 확정·수정 재개마다 1 올립니다. 실패·처리 중·완료 결과 재조회와 표지·맵·좌표 변경에서는 올리지 않습니다. `CASES.chat_context_revision`에 저장합니다.
- 최초 채팅 접수 시 현재 버전을 해당 관리자 요청에 저장합니다(`ADMIN_CASE_MESSAGES.chat_context_revision`). 클라이언트가 요청 본문에 버전을 보낼 필요는 없습니다.
- 실패한 동일 `requestId`·메시지는 `DRAFT`이고 접수 버전과 현재 버전이 같을 때만 현재 원문·초안·대화 이력으로 재실행합니다. 실패한 발화는 생성용 대화 이력에서 제외합니다. 버전 비교·접수는 사건 기준의 짧은 잠금 안에서 처리합니다.
- 버전이 달라졌으면 `409 CASE_CHAT_CONTEXT_CHANGED`와 `{ currentRevision: number }`를 반환합니다. 프론트엔드는 최신 detail·messages를 다시 받고, 사용자가 확인한 내용을 새 `requestId`로 전송합니다. 오래된 요청을 자동으로 새 요청으로 바꾸지 않습니다.
- 같은 ID의 입력 비교 및 완료·처리 중 조회는 현재 사건의 수정 가능 상태 검사보다 먼저 수행합니다(관리자 권한 검사는 항상 먼저). 완료 요청은 확정·출시 후에도 당시 성공 응답을 반환하며 AI를 다시 호출하지 않습니다.
- 완료 응답의 `draft`, `draftUpdatedAt`, `draftIssues`는 당시 스냅샷입니다. 재전송·폴링으로 복구한 뒤에는 detail·messages를 다시 조회해 최신 메모장을 표시합니다. 과거 초안을 최신 상태에 덮어쓰지 않습니다.
- 최초 접수 때 관리자 발화를 저장하며, 재시도는 같은 발화의 상태를 갱신하고 중복 발화를 만들지 않습니다. 정상 완료 시 AI 발화·초안·버전 증가·요청 성공 결과를 한 번에 저장합니다. 관리자 발화의 `response_json`에 전체 `AdminChatResponse` 스냅샷을 보존하며 이 저장 컬럼 자체는 메시지 DTO에 노출하지 않습니다.

**요청 본문** `AdminChatRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `requestId` | string | O | UUID |
| `message` | string | O | 1~10000자 |

**성공 응답** `200` `AdminChatResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `requestId` | string | |
| `adminMessage` | [AdminCaseMessageResponse](#get-apiadmincasescaseidmessages) | |
| `aiMessage` | [AdminCaseMessageResponse](#get-apiadmincasescaseidmessages) | `toolChanges` 포함 |
| `draft` | Draft | 갱신된 전체 초안. 메모장 패널을 이 값으로 다시 그린다 |
| `draftUpdatedAt` | string | |
| `draftIssues` | [DraftIssue](admin-draft-schema.md#콘텐츠-확정-시-검증과-반영)[] | 갱신된 초안의 확정 전 검사 결과 |

`inputInfo`는 `aiMessage.inputInfo`에 담깁니다. 채팅 이력에도 저장되므로(`ADMIN_CASE_MESSAGES.input_info`) 새로고침 후에도 다시 볼 수 있습니다.

#### InputInfo

| 필드 | 타입 | 설명 |
|---|---|---|
| `mode` | `FULL` \| `HISTORY_SUMMARIZED` \| `SOURCE_CHUNKED` | 원문·대화 이력 전체 입력 / 오래된 대화 이력 요약 / 원문 분할 |
| `inputTokens` | number \| null | 확인된 입력 토큰 수 |
| `note` | string \| null | 요약·분할 범위 등 설명 |

**처리 중** `202` `{ requestId: string, status: "PROCESSING", acceptedAt: string }` — 완료 여부는 [messages](#get-apiadmincasescaseidmessages)에서 해당 `requestId`의 `status`로 확인합니다.

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | |
| 400 | `LLM_INPUT_TOO_LARGE` | 축약·분할로도 입력 한도를 맞출 수 없음 |
| 409 | `SOURCE_TEXT_REQUIRED` | 원문 미업로드 |
| 409 | `AI_REQUEST_ID_REUSED` | 같은 `requestId`에 다른 메시지 |
| 409 | `CASE_CHAT_CONTEXT_CHANGED` | 실패 요청 접수 후 원문·대화·초안·제작 상태 변경. `detail: { "currentRevision": number }` |
| 502 | `CASE_CHAT_FAILED` | Claude API 실패. 초안 변경 없음 |

### POST /api/admin/cases/{caseId}/confirm

초안을 검증해 실제 콘텐츠 테이블에 반영하고 `CONTENT_CONFIRMED`로 바꿉니다. 아직 플레이어에게 공개되지 않습니다. 검증 규칙은 [admin-draft-schema.md](admin-draft-schema.md#콘텐츠-확정-시-검증과-반영)를 따릅니다.

**요청 본문**: 없음

**성공 응답** `200` `AdminCaseConfirmResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `status` | `CaseStatus` | `CONTENT_CONFIRMED` |
| `contentUpdatedAt` | string | |
| `warnings` | DraftIssue[] | 확정을 막지 않은 경고 |
| `removedHotspots` | `{ "type", "refId" }[]` | 삭제된 인물·물증을 가리켜 제거된 배치 |

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `DRAFT_INVALID` | 검증 오류. `detail: { "issues": DraftIssue[] }` |

### POST /api/admin/cases/{caseId}/reopen

`CONTENT_CONFIRMED → DRAFT`. 콘텐츠를 다시 채팅으로 수정할 수 있게 됩니다.

- 미종료 테스트 세션이 있으면 거부합니다(`409 TEST_ROOM_UNFINISHED`).
- **종료된 테스트 세션과 그 대화·조사·결과 데이터를 삭제**합니다. 프론트엔드는 호출 전에 "기존 테스트 플레이 기록이 초기화됩니다" 확인을 받습니다. 제작 채팅·초안·사용량 로그는 유지합니다.
- 테스트 완료 확인을 초기화합니다.

**성공 응답** `200` `{ "status": "DRAFT", "deletedTestRoomCount": number }`

---

## 맵 · 좌표

맵·타일셋·스프라이트 파일은 프론트엔드 정적 에셋(`frontend/public/assets/`)으로 배포됩니다. 백엔드는 검증을 위해 설정값 `shelved.assets.base-path`가 가리키는 같은 폴더의 파일을 읽습니다. 사용 가능한 `spriteKey` 목록은 `assets/manifest.json` 한 곳에서 관리합니다.

### GET /api/admin/assets

맵 설정과 NPC 스프라이트 지정에 쓸 선택 목록입니다. 백엔드가 `assets/manifest.json`과 각 맵 파일을 읽어 반환합니다. 사건과 무관하므로 사건 상태 검사를 하지 않습니다.

**성공 응답** `200` `AdminAssetListResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `maps` | object[] | `{ mapUrl: string, widthPx: number, heightPx: number, layers: string[] }`. 크기는 양의 정수 픽셀. `layers`는 충돌 레이어 선택지 |
| `spriteKeys` | string[] | NPC에 지정할 수 있는 스프라이트 식별자 |

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 500 | `ASSET_MANIFEST_INVALID` | `manifest.json` 또는 그 안의 맵 파일을 읽을 수 없음 |

### PUT /api/admin/cases/{caseId}/map

맵 설정을 저장합니다(없으면 생성). 좌표는 맵 좌상단 원점의 월드 픽셀 단위입니다.

**요청 본문** `AdminCaseMapUpdateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `mapUrl` | string | O | `/assets/maps/`로 시작하는 Tiled JSON 경로 |
| `spawnX`, `spawnY` | number | O | 0 이상의 정수. 맵 범위 안 |
| `collisionLayer` | string | O | 맵에 존재하는 레이어 이름 |
| `interactionRadiusPx` | number | O | 1 이상의 정수 |

**성공 응답** `200` `AdminCaseMapResponse` (요청과 같은 필드)

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `MAP_INVALID` | 맵 파일을 읽을 수 없음, 충돌 레이어 없음, 시작점이 맵 밖이거나 이동 불가 영역. `detail: { "issues": string[] }` |

### PUT /api/admin/cases/{caseId}/hotspots

NPC·물증 배치를 **전체 교체**합니다. 맵 설정이 먼저 있어야 합니다.

**요청 본문** `AdminHotspotUpdateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `hotspots` | object[] | O | `{ "type": HotspotType, "refId": number, "x": number, "y": number }` |

- `refId`는 이 사건의 `suspectId`(`SUSPECT`) 또는 `evidenceId`(`EVIDENCE`)입니다.
- 같은 대상을 두 번 배치할 수 없습니다. `x`, `y`는 0 이상의 정수이며 맵 범위 안이어야 합니다. 빈 배열은 허용하되 필수 배치 누락은 출시 검사에서 차단합니다.

**성공 응답** `200` `{ "hotspots": HotspotResponse[] }`

#### HotspotResponse

| 필드 | 타입 |
|---|---|
| `hotspotId` | number |
| `type` | `HotspotType` |
| `refId` | number |
| `x`, `y` | number |

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `HOTSPOT_INVALID` | 다른 사건·존재하지 않는 대상, 중복 배치, 맵 범위 밖, 이동 불가 영역. `detail: { "issues": string[] }` |
| 409 | `MAP_NOT_CONFIGURED` | 맵 설정 전 |

---

## 관리자 테스트

테스트 세션은 일반 세션과 같은 규칙으로 동작하며 `is_test=true`입니다. 생성 후 **플레이는 일반 세션 API(`/api/rooms/{roomId}/...`)를 그대로 사용**합니다. 테스트 결과는 랭킹과 일반 세션 목록(`GET /api/rooms`)에 포함되지 않습니다.

### GET /api/admin/cases/{caseId}/test-rooms

**성공 응답** `200` `List<AdminTestRoomResponse>` — 최신 순

| 필드 | 타입 | 설명 |
|---|---|---|
| `roomId` | number | |
| `status` | `RoomStatus` | |
| `createdAt` | string | |
| `endedAt` | string \| null | |
| `durationSec` | number \| null | 종료된 경우 |
| `countsForVerification` | boolean | 현재 내용(`contentUpdatedAt` 이후)으로 생성되어 종료된 테스트인지 |

### POST /api/admin/cases/{caseId}/test-rooms

**성공 응답** `201` `{ "roomId": number, "status": "READY" }`

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `MAP_NOT_CONFIGURED` | 맵 설정 전 |
| 409 | `TEST_ROOM_UNFINISHED` | 이미 미종료 테스트가 있음 (한 번에 하나) |

### DELETE /api/admin/cases/{caseId}/test-rooms/{roomId}

미종료 테스트를 중단합니다. 처리 중인 AI 요청을 먼저 무효화한 뒤 세션과 관련 플레이 데이터를 삭제합니다. 결과 기록은 만들지 않으며 사용량 로그는 보존합니다.

**성공 응답** `200`, `data: null`

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 404 | `TEST_ROOM_NOT_FOUND` | 이 사건의 테스트 세션이 아님 (일반 세션 포함) |
| 409 | `TEST_ROOM_ALREADY_ENDED` | 이미 종료됨 (종료된 테스트는 수정 재개 시 함께 삭제) |

### POST /api/admin/cases/{caseId}/test-verification

관리자가 현재 내용으로 테스트를 마쳤음을 확인합니다. `CASES.test_verified_at`에 현재 시각을 저장합니다.

- 미종료 테스트가 없어야 합니다.
- `contentUpdatedAt` 이후에 생성되어 종료된 테스트 세션이 1개 이상 있어야 합니다. 없으면 `409 TEST_NOT_COMPLETED`.

**성공 응답** `200` `{ "testVerifiedAt": string }`

---

## 출시 · 복제

### GET /api/admin/cases/{caseId}/publish-check

출시 버튼을 누르기 전에 수정할 항목을 미리 보여줍니다. 출시 API와 같은 검사를 수행합니다.

**성공 응답** `200` `AdminPublishCheckResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `publishable` | boolean | |
| `issues` | PublishIssue[] | `{ "code", "target", "message" }` |

| `code` | 검사 내용 |
|---|---|
| `CARD_INFO_MISSING` | 카드 표시명·난이도·키워드·표지 이미지 누락 |
| `CONTENT_MISSING` | NPC·물증·범인·트릭 요소·사전 정보·리빌 해설 누락 |
| `MAP_MISSING` | 맵 설정 없음, 맵 파일·참조 에셋 로드 실패, 충돌 레이어 없음 |
| `SPAWN_INVALID` | 시작점이 맵 밖·이동 불가 영역 |
| `HOTSPOT_MISSING` | 대화 가능 NPC 또는 물증이 배치되지 않음 |
| `HOTSPOT_INVALID` | 다른 사건·존재하지 않는 대상 참조, 맵 밖·이동 불가 영역 좌표 |
| `SPRITE_INVALID` | 배치된 NPC의 `spriteKey`가 없거나 에셋 목록에 없음 |
| `TEST_UNFINISHED` | 미종료 테스트 세션 있음 |
| `TEST_NOT_VERIFIED` | 현재 내용의 테스트 완료 확인 없음 |

`target`은 문제 대상(예: `"suspect:3"`, `"evidence:7"`, `"map"`), `message`는 한국어 설명입니다.

### POST /api/admin/cases/{caseId}/publish

`CONTENT_CONFIRMED → PUBLISHED`. 출시 후에는 콘텐츠·맵·배치·표지·용어집을 수정할 수 없습니다.

**요청 본문**: 없음

**성공 응답** `200` `{ "status": "PUBLISHED" }`

**실패 응답** (공통 외)

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `PUBLISH_CHECK_FAILED` | 검사 실패. `detail: { "issues": PublishIssue[] }` |

### POST /api/admin/cases/{caseId}/clone

공개된 사건을 새 ID의 `DRAFT` 사건으로 복제합니다. 원문·초안·확정 콘텐츠·표지·맵·배치를 복사하고 내부 참조를 새 사건 기준으로 연결합니다. 제작 채팅 이력, 플레이 세션·결과·랭킹, 테스트 완료 확인은 복제하지 않습니다.

**성공 응답** `201` `{ "caseId": number, "status": "DRAFT" }`

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 409 | `CASE_NOT_PUBLISHED` | 공개된 사건만 복제 가능 |
