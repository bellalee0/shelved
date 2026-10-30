# API 명세 (사전 설계)

> 백엔드와 프론트엔드가 병렬로 개발하기 위해 구현 전에 작성하는 API 계약입니다.
> 구현 후 Swagger UI(springdoc)로 이 명세와 실제 구현이 일치하는지 확인합니다.
> 작성·변경 절차는 [common.md의 API 명세](../conventions/common.md#api-명세)를 따릅니다.

## 목차

1. [문서 구성](#문서-구성)
2. [공통 규칙](#공통-규칙)
3. [공통 응답 형식](#공통-응답-형식)
4. [HTTP 상태 코드](#http-상태-코드)
5. [인증과 요청 헤더](#인증과-요청-헤더)
6. [AI 요청 처리 (requestId)](#ai-요청-처리-requestid)
7. [공통 enum](#공통-enum)

---

## 문서 구성

| 파일 | 내용 | 백엔드 도메인 |
|---|---|---|
| [errors.md](errors.md) | 전체 예외 코드(`ErrorCode`) 목록 | `common` |
| [auth-user.md](auth-user.md) | Google 로그인, 토큰 재발급·로그아웃, 내 정보·닉네임 | `auth`, `user` |
| [cases-ranking.md](cases-ranking.md) | 사건 카드 목록, 사건별 랭킹 | `cases`, `ranking` |
| [room.md](room.md) | 세션 생성·목록·시작·하트비트·일시정지·재개, 상태 복원, 사전 정보, 맵, 결과(리빌), AI 요청 상태 | `room` |
| [investigation.md](investigation.md) | NPC 질문·대화 기록, 물증 조사, 개인 노트 | `npc`, `evidence`, `note` |
| [hint-accusation.md](hint-accusation.md) | 질문형 힌트·무료 보충 질문, 범인·트릭 지목 | `hint`, `accusation` |
| [admin-casebuilder.md](admin-casebuilder.md) | 관리자 사건 제작·확정·맵·테스트·출시·복제 | `casebuilder` |
| [admin-draft-schema.md](admin-draft-schema.md) | 제작 초안(`draft`) JSON 구조와 제작 채팅 AI tool 정의 | `casebuilder` |
| [admin-llm-usage.md](admin-llm-usage.md) | AI 사용량 집계, 예산 설정 | `llmusage` |

화면별로 어떤 응답 필드를 쓰는지는 [와이어프레임](../wireframes/wireframes.html)의 각 화면 아래 표에 정리되어 있습니다. 명세를 바꾸면 와이어프레임 표도 함께 고칩니다.

각 API는 다음 순서로 적습니다: 설명 → 인증 → 요청(헤더·경로·쿼리·본문) → 성공 응답 → 실패 응답 → 예시.
예시에는 **가상의 사건만** 사용합니다. ([정답 정보 보호](../conventions/common.md#정답-정보-보호-공개-저장소))

---

## 공통 규칙

| 항목 | 규칙 |
|---|---|
| 기본 경로 | 모든 API는 `/api`로 시작한다. 관리자 API는 `/api/admin`으로 시작한다 |
| 데이터 형식 | 요청·응답 본문은 JSON(`application/json`). 파일 업로드만 `multipart/form-data` |
| 필드명 | camelCase. 백엔드 DTO 필드명 = 프론트엔드 타입 필드명 |
| ID | 엔티티 ID는 정수(`Long` → TS `number`). `requestId`, `connectionId`는 문자열(UUID) |
| 날짜·시각 | ISO-8601, 오프셋 없는 서버 현지 시각(Asia/Seoul). 예: `"2026-10-05T14:30:00"` |
| 시간 길이 | 필드명에 단위를 붙인다. 밀리초 `...Ms`, 초 `...Sec` (예: `remainingMs`, `durationSec`) |
| null | 값이 없는 필드는 생략하지 않고 `null`로 내려준다. 빈 목록은 `[]` |
| 페이지네이션 | 쿼리 `page`(0부터), `size`(기본 20, 최대 100). 응답은 [PageResponse](#pageresponse) |
| 문자열 입력 | 앞뒤 공백을 제거한 뒤 검증한다. 단, 개인 노트 `content`는 공백·줄바꿈을 포함해 그대로 보존한다. 길이는 Java/JavaScript의 UTF-16 코드 단위로 세며 각 API의 제한을 적용한다 |

---

숫자 ID는 양의 정수, 페이지·횟수·버전은 0 이상의 정수입니다. 좌표·반경·맵 크기는 정수 픽셀 단위입니다. 요청에서 범위를 벗어나거나 소수인 값은 `400 INVALID_INPUT`(본문) 또는 `400 INVALID_PARAMETER`(경로·쿼리)입니다.

## 공통 응답 형식

모든 응답은 `CommonResponse`로 감쌉니다. 기존 프로젝트(burnchuck, TripMate)와 같은 형식입니다.

```json
{
  "success": true,
  "message": "세션 생성 성공",
  "data": { },
  "timestamp": "2026-10-05T14:30:00.123"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `success` | boolean | 성공 여부 |
| `message` | string | 성공 시 `SuccessMessage`, 실패 시 `ErrorCode`의 메시지. **실패 메시지는 사용자에게 그대로 보여준다** |
| `data` | object \| null | 성공 시 API별 응답 데이터. 데이터가 없으면 `null`. 실패 시 [ErrorData](#실패-응답) |
| `timestamp` | string | 응답 생성 시각 |

### 실패 응답

실패 시 `data`에 예외 코드 이름을 담습니다. 사용자에게는 `message`를 보여주고, 프론트엔드의 분기(토큰 재발급, 노트 충돌 처리 등)는 `data.code`로 합니다. 메시지 문자열로 분기하지 않습니다.

```json
{
  "success": false,
  "message": "다른 곳에서 노트가 먼저 저장되었습니다. 서버 내용과 작성 중인 내용을 확인해 주세요.",
  "data": {
    "code": "NOTE_REVISION_CONFLICT",
    "detail": { "currentRevision": 8, "currentContent": "서버에 저장된 노트 내용" }
  },
  "timestamp": "2026-10-05T14:30:00.123"
}
```

| 필드 | 타입 | 설명 |
|---|---|---|
| `data.code` | string | `ErrorCode` enum 이름 ([errors.md](errors.md)) |
| `data.detail` | object \| null | 일부 예외에서만 제공하는 추가 정보. 형태는 [errors.md](errors.md)에 코드별로 적는다 |

> 기존 프로젝트의 `CommonResponse.exception(message)`는 `data`가 항상 `null`이었습니다. 이 프로젝트는 프론트엔드가 같은 409라도 원인별로 다르게 처리해야 하므로(노트 충돌, 세션 상태 오류 등) `data`에 코드를 추가했습니다. 응답의 겉모양(`success`/`message`/`data`/`timestamp`)은 그대로입니다.

### PageResponse

페이지 조회 API의 `data` 형식입니다. (burnchuck `PageResponse`와 동일)

```json
{
  "content": [ ],
  "totalElements": 42,
  "totalPages": 3,
  "size": 20,
  "number": 0
}
```

---

## HTTP 상태 코드

상태 코드는 아래 목록만 사용합니다. 세부 원인은 상태 코드를 늘리지 않고 `message`와 `data.code`로 구분합니다.

| 코드 | 사용하는 경우 |
|---|---|
| `200 OK` | 조회, 수정, 삭제, 로그인·재발급·로그아웃, 결과를 바로 반환하는 액션(NPC 질문, 힌트, 지목, 시작·일시정지 등) |
| `201 Created` | 새 리소스 생성 (세션, 사건 초안, 테스트 세션, 사건 복제) |
| `202 Accepted` | 같은 `requestId`의 AI 요청이 아직 처리 중이라 결과 대신 처리 상태를 반환 |
| `400 Bad Request` | 입력값 검증 실패, 잘못된 파일, 정책상 차단된 질문(직접적인 정답 요구) |
| `401 Unauthorized` | 로그인 필요, 토큰 없음·만료·위조 |
| `403 Forbidden` | 권한 없음(관리자 전용), 닉네임 미설정 |
| `404 Not Found` | 리소스 없음, **또는 본인이 접근할 수 없는 리소스**(다른 사람의 세션, 비공개 사건). 존재 여부를 노출하지 않기 위해 403 대신 404를 쓴다 |
| `405 Method Not Allowed` | 지원하지 않는 HTTP 메서드 (전역 예외 핸들러에서만 사용) |
| `409 Conflict` | 현재 상태에서 할 수 없는 요청 (세션 상태·남은 횟수·버전 충돌·다른 요청 처리 중·사건 상태 등) |
| `429 Too Many Requests` | 채점 재시도 최소 간격(잠정 10초)이 지나기 전에 다시 요청 |
| `500 Internal Server Error` | 서버 내부 오류 |
| `502 Bad Gateway` | Claude API 호출 실패·시간 초과·응답 형식 오류. **기회는 차감되지 않으며 같은 요청으로 재시도 가능** |

---

## 인증과 요청 헤더

| 헤더 | 값 | 대상 |
|---|---|---|
| `Authorization` | `Bearer {accessToken}` | 인증이 필요한 모든 API |
| `X-Connection-Id` | 시작·재개 API가 발급한 `connectionId` | 세션을 조작하는 API (아래 표) |

- 토큰 발급·보관·갱신 방식은 [auth-user.md](auth-user.md#토큰-처리-흐름)를 따릅니다.
- 인증이 필요 없는 API: 로그인·토큰 재발급·로그아웃, 사건 카드 목록(`GET /api/cases`). 그 외 모든 API는 로그인이 필요합니다.
- 닉네임을 설정하지 않은 사용자는 로그인·내 정보·닉네임 설정 API 외에는 `403 NICKNAME_REQUIRED`를 받습니다.

### X-Connection-Id (조작 연결 식별자)

같은 세션을 한 브라우저 화면에서만 조작하도록, 수사 시작·이어하기 때 서버가 `connectionId`를 발급합니다(기획서 3.5절). 프론트엔드는 이 값을 메모리에 들고 있다가 조작 API의 헤더로 보냅니다. 경로·본문이 아닌 헤더로 보내는 이유는, 인증 토큰처럼 "누가/어느 연결에서 보냈는가"를 나타내는 요청 맥락 정보이기 때문입니다. 공통 인터셉터에서 한 번에 붙일 수 있습니다.

| 필요 여부 | API |
|---|---|
| 필수 | 하트비트, NPC 질문, 물증 조사, 노트 저장, 새 힌트, 보충 질문, 지목 제출(새 답안) |
| 불필요 | 모든 조회(GET), 시작, 재개, 일시정지, 실패한 지목의 같은 답안 재채점 |

- 헤더가 없으면 `400 CONNECTION_ID_REQUIRED`, 현재 연결이 아니면 `409 CONNECTION_REPLACED`입니다. `CONNECTION_REPLACED`를 받은 화면은 조작을 멈추고 "다른 화면에서 이어하기가 실행되었습니다" 안내 후 복원 화면으로 이동합니다.
- 일시정지는 새로고침 직후(이전 `connectionId`를 잃은 상태)에도 호출해야 하므로 헤더를 요구하지 않습니다. 일시정지는 시간을 멈추기만 하므로 이전 연결에서 호출되어도 안전합니다.
- 이미 저장된 결과를 같은 `requestId`로 다시 받는 경우에는 연결 검사를 하지 않습니다.

---

## AI 요청 처리 (requestId)

NPC 질문, 새 힌트, 보충 질문, 지목은 Claude API를 호출합니다. 중복 실행·중복 차감을 막기 위해 요청 본문에 `requestId`(UUID)를 넣습니다. (기획서 3.6절)

| 상황 | 응답 |
|---|---|
| 새 `requestId`, 정상 완료 | `200` + 결과 |
| 같은 `requestId`가 아직 처리 중 | `202` + [AiRequestStatusResponse](room.md#airequeststatusresponse) (결과는 상태 조회 API로 확인) |
| 같은 `requestId`가 이미 완료 | `200` + 저장된 결과 (다시 실행·차감하지 않음) |
| 같은 `requestId`가 이전에 실패 | 같은 입력이면 다시 실행 (실행 번호 증가) |
| 같은 `requestId`인데 입력이 다름 | `409 AI_REQUEST_ID_REUSED` |
| 같은 대상에 다른 요청이 처리 중 | `409 AI_REQUEST_IN_PROGRESS` (NPC별 1개, 힌트 1개, 힌트별 보충 1개, 지목 1개) |
| Claude API 실패·시간 초과·형식 오류 | `502` + 유형별 실패 코드. 기회 미차감, 같은 `requestId`·입력으로 재시도 |
| 처리 중 세션이 최종 종료됨 | `409 ROOM_ENDED_DURING_REQUEST`. 결과는 기록되지 않음 |

- 프론트엔드: 새로 전송할 때만 `crypto.randomUUID()`로 새 ID를 만들고, 재시도는 같은 ID와 같은 입력을 보냅니다. ([frontend.md](../conventions/frontend.md#5-ai-요청의-requestid-처리))
- `requestId`는 세션 안에서 요청 유형과 관계없이 유일해야 합니다.
- 직접적인 정답 요구로 차단된 질문(`400 QUESTION_BLOCKED`)은 AI 요청으로 접수하지 않으므로, 같은 `requestId`를 다른 질문에 다시 써도 됩니다.
- 차단 여부는 NPC 질문·새 힌트·보충 질문을 접수하기 전에 작은 모델의 의도 분류 호출로 판정합니다(기획서 2.4절). 분류 호출이 실패하면 요청을 접수하지 않고 해당 API의 `502` 실패 코드(`NPC_ANSWER_FAILED`, `HINT_GENERATION_FAILED`, `HINT_FOLLOWUP_FAILED`)를 반환합니다. 기회는 차감되지 않으며 같은 `requestId`·입력으로 다시 보내면 됩니다.

---

### 저장된 결과와 현재 상태

완료된 요청 재전송과 AI 상태 조회의 `result`는 **최초 성공 당시의 스냅샷**입니다. `hintCount`, `accusationCount`, `roomStatus`, `followupAvailable`도 당시 값이며 현재 상태로 재계산하지 않습니다. 프론트엔드는 복구·재전송 결과에서 대화·답안을 복원한 뒤 `GET /api/rooms/{roomId}/state`를 다시 조회해 현재 시간·횟수·화면 전환을 결정합니다. 보충 질문 버튼은 최신 `GET hints`로 갱신합니다. 과거 응답으로 현재 상태를 덮어쓰지 않습니다.

### 관리자 제작 채팅의 예외

관리자 채팅의 `requestId`는 세션이 아닌 **사건 안에서 유일**합니다. `202`는 `{ requestId: string, status: "PROCESSING", acceptedAt: string }`이며 게임용 `AiRequestStatusResponse`를 사용하지 않습니다. 상태는 관리자 `GET messages`로 확인합니다. 사건별 순차 처리 오류는 `CASE_CHAT_IN_PROGRESS`, 실패 후 제작 상태 변경은 `CASE_CHAT_CONTEXT_CHANGED`로 구분합니다. 완료 결과의 과거 초안 처리와 재시도 조건은 [관리자 채팅](admin-casebuilder.md#post-apiadmincasescaseidchat)을 따릅니다.

## 공통 enum

| enum | 값 | 설명 |
|---|---|---|
| `UserRole` | `PLAYER`, `ADMIN` | |
| `Difficulty` | `EASY`, `NORMAL`, `HARD` | 쉬움·보통·어려움 |
| `CaseStatus` | `DRAFT`, `CONTENT_CONFIRMED`, `PUBLISHED` | 관리자 API에서만 노출 |
| `RoomStatus` | `READY`, `IN_PROGRESS`, `PAUSED`, `CLEARED`, `FAILED`, `EXPIRED` | `CLEARED`/`FAILED`/`EXPIRED`는 종료 상태 |
| `AiRequestType` | `NPC_ASK`, `HINT`, `HINT_FOLLOWUP`, `ACCUSATION` | 게임플레이 AI 요청 유형 |
| `AiRequestStatus` | `PROCESSING`, `SUCCEEDED`, `FAILED` | |
| `GradingStatus` | `PENDING`, `COMPLETED`, `ERROR` | 지목 채점 상태 |
| `Verdict` | `CORRECT`, `PARTIAL`, `INCORRECT` | 정답·부분 정답·오답 |
| `MessageSenderType` | `USER`, `NPC` | NPC 대화 발화자 |
| `AdminMessageSenderType` | `ADMIN`, `AI` | 관리자 제작 채팅 발화자 |
| `HotspotType` | `SUSPECT`, `EVIDENCE` | 맵 배치 대상 |
| `LlmRequestType` | `NPC_CHAT`, `HINT`, `HINT_FOLLOWUP`, `GRADING`, `CASE_GENERATION`, `QUESTION_FILTER` | 사용량 로그 유형. `QUESTION_FILTER`는 직접 정답 요구 차단 판정 호출 |
