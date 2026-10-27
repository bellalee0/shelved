# 예외 코드 목록 (ErrorCode)

> 백엔드 `common/exception/ErrorCode` enum과 1:1로 대응합니다. 실패 응답의 `data.code`에 enum 이름이, `message`에 아래 메시지가 담깁니다. ([README](README.md#실패-응답))
> 메시지는 사용자에게 그대로 노출되므로 문장형으로 씁니다. 구현 중 문구를 다듬는 것은 자유지만, **코드 이름과 상태 코드를 바꾸면 Breaking Change**입니다.

## 공통 (전역 예외 핸들러)

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `INVALID_INPUT` | 400 | (`@Valid` 검증 메시지 중 첫 번째) | `{ "field" }` |
| `INVALID_REQUEST_BODY` | 400 | 요청 본문 형식이 올바르지 않습니다. | |
| `INVALID_PARAMETER` | 400 | 파라미터 값이 올바르지 않습니다. | `{ "field" }` |
| `API_NOT_FOUND` | 404 | 요청한 API를 찾을 수 없습니다. | |
| `METHOD_NOT_ALLOWED` | 405 | 지원하지 않는 HTTP 메서드입니다. | |
| `INTERNAL_SERVER_ERROR` | 500 | 서버 내부 오류가 발생했습니다. | |

## 인증 · 사용자

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `LOGIN_REQUIRED` | 401 | 로그인이 필요합니다. | |
| `INVALID_TOKEN` | 401 | 유효하지 않은 토큰입니다. | |
| `EXPIRED_TOKEN` | 401 | 로그인이 만료되었습니다. | |
| `INVALID_REFRESH_TOKEN` | 401 | 로그인 정보가 만료되었습니다. 다시 로그인해 주세요. | |
| `GOOGLE_TOKEN_INVALID` | 401 | Google 로그인 정보를 확인할 수 없습니다. 다시 시도해 주세요. | |
| `ACCESS_DENIED` | 403 | 접근 권한이 없습니다. | |
| `NICKNAME_REQUIRED` | 403 | 닉네임을 먼저 설정해 주세요. | |
| `USER_NOT_FOUND` | 404 | 사용자가 존재하지 않습니다. | |
| `NICKNAME_EXIST` | 409 | 이미 사용 중인 닉네임입니다. | |

- `EXPIRED_TOKEN`을 받으면 프론트엔드는 토큰을 재발급하고 원래 요청을 재시도합니다. `INVALID_TOKEN`·`INVALID_REFRESH_TOKEN`은 로그인 화면으로 이동합니다.

## 사건 · 랭킹

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `CASE_NOT_FOUND` | 404 | 사건이 존재하지 않습니다. | |

## 세션

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `CONNECTION_ID_REQUIRED` | 400 | 수사 연결 정보가 없습니다. 게임 화면을 다시 열어 주세요. | |
| `ROOM_NOT_FOUND` | 404 | 세션이 존재하지 않습니다. | |
| `ROOM_ALREADY_EXISTS` | 409 | 이 사건에 진행 중인 수사가 있습니다. 이어서 진행해 주세요. | `{ "roomId" }` |
| `ROOM_NOT_READY` | 409 | 이미 시작된 수사입니다. | |
| `ROOM_NOT_PAUSED` | 409 | 일시정지 상태의 수사만 이어할 수 있습니다. | |
| `ROOM_NOT_IN_PROGRESS` | 409 | 진행 중인 수사가 아닙니다. | |
| `ROOM_TIME_EXPIRED` | 409 | 제한시간이 끝났습니다. | |
| `ROOM_NOT_ENDED` | 409 | 아직 종료되지 않은 수사입니다. | |
| `CONNECTION_REPLACED` | 409 | 다른 화면에서 수사를 이어하고 있습니다. | |
| `ROOM_ENDED_DURING_REQUEST` | 409 | 수사가 종료되어 이 요청은 반영되지 않았습니다. | |

## AI 요청 공통

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `AI_REQUEST_NOT_FOUND` | 404 | 요청을 찾을 수 없습니다. | |
| `AI_REQUEST_ID_REUSED` | 409 | 같은 요청 식별자로 다른 내용을 보낼 수 없습니다. | |
| `AI_REQUEST_IN_PROGRESS` | 409 | 이전 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. | `{ "requestId" }` 처리 중인 요청 |
| `AI_REQUEST_SUPERSEDED` | 409 | 새 답안으로 대체된 제출은 다시 채점할 수 없습니다. | |

## NPC · 물증 · 노트

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `QUESTION_BLOCKED` | 400 | 범인이나 정답을 직접 묻는 질문에는 답할 수 없습니다. 인물의 행적, 목격한 사실, 물증에 관해 질문해 보세요. | |
| `NPC_NOT_CONVERSABLE` | 400 | 대화할 수 없는 인물입니다. | |
| `SUSPECT_NOT_FOUND` | 404 | 인물이 존재하지 않습니다. | |
| `EVIDENCE_NOT_FOUND` | 404 | 물증이 존재하지 않습니다. | |
| `NOTE_REVISION_CONFLICT` | 409 | 다른 곳에서 노트가 먼저 저장되었습니다. 서버 내용과 작성 중인 내용을 확인해 주세요. | `{ "currentRevision", "currentContent" }` |
| `NPC_ANSWER_FAILED` | 502 | NPC의 답변을 받지 못했습니다. 같은 질문을 다시 보내 주세요. | `{ "requestId" }` |

## 힌트 · 지목

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `HINT_NOT_FOUND` | 404 | 힌트가 존재하지 않습니다. | |
| `HINT_LIMIT_EXCEEDED` | 409 | 남은 힌트 횟수가 없습니다. | |
| `HINT_FOLLOWUP_ALREADY_USED` | 409 | 이 힌트의 보충 질문을 이미 사용했습니다. | |
| `ACCUSATION_LIMIT_EXCEEDED` | 409 | 남은 지목 기회가 없습니다. | |
| `GRADING_RETRY_TOO_SOON` | 429 | 잠시 후 다시 채점할 수 있습니다. | `{ "retryAvailableAt" }` |
| `HINT_GENERATION_FAILED` | 502 | 힌트를 받지 못했습니다. 힌트 횟수는 차감되지 않았습니다. | `{ "requestId" }` |
| `HINT_FOLLOWUP_FAILED` | 502 | 보충 설명을 받지 못했습니다. 보충 질문 기회는 그대로 남아 있습니다. | `{ "requestId" }` |
| `GRADING_FAILED` | 502 | 채점 중 오류가 발생했습니다. 지목 기회는 차감되지 않았으며 같은 답안으로 다시 채점할 수 있습니다. | `{ "requestId", "accusationId", "retryAvailableAt" }` |

## 관리자 사건 제작

| 코드 | 상태 | 메시지 | detail |
|---|---|---|---|
| `SOURCE_TEXT_INVALID_FILE` | 400 | UTF-8 인코딩의 .txt 파일만 업로드할 수 있습니다. | |
| `COVER_IMAGE_INVALID_FILE` | 400 | jpg, png, webp 이미지만 업로드할 수 있습니다. | |
| `FILE_TOO_LARGE` | 400 | 파일 크기가 허용 범위를 넘었습니다. | `{ "maxBytes" }` |
| `LLM_INPUT_TOO_LARGE` | 400 | 원문과 대화 내용이 AI 입력 한도를 넘어 처리할 수 없습니다. 원문 분할 처리가 필요합니다. | `{ "estimatedInputTokens", "limitTokens" }` |
| `MAP_INVALID` | 400 | 맵 설정을 확인해 주세요. | `{ "issues": string[] }` |
| `HOTSPOT_INVALID` | 400 | 배치 좌표를 확인해 주세요. | `{ "issues": string[] }` |
| `TEST_ROOM_NOT_FOUND` | 404 | 테스트 세션이 존재하지 않습니다. | |
| `CASE_PUBLISHED_LOCKED` | 409 | 공개된 사건은 수정할 수 없습니다. 초안으로 복제해 수정해 주세요. | |
| `CASE_NOT_DRAFT` | 409 | 초안 상태에서만 할 수 있습니다. 수정 재개 후 다시 시도해 주세요. | |
| `CASE_NOT_CONTENT_CONFIRMED` | 409 | 콘텐츠 확정 상태에서만 할 수 있습니다. | |
| `CASE_NOT_PUBLISHED` | 409 | 공개된 사건만 복제할 수 있습니다. | |
| `CASE_CHAT_IN_PROGRESS` | 409 | 제작 채팅을 처리하고 있습니다. 완료된 뒤 다시 시도해 주세요. | `{ "requestId" }` |
| `CASE_AI_REQUEST_IN_PROGRESS` | 409 | 테스트 세션의 AI 요청을 처리하고 있습니다. 잠시 후 다시 시도해 주세요. | |
| `SOURCE_TEXT_REQUIRED` | 409 | 원문을 먼저 업로드해 주세요. | |
| `DRAFT_INVALID` | 409 | 초안에 수정할 항목이 있습니다. | `{ "issues": DraftIssue[] }` |
| `MAP_NOT_CONFIGURED` | 409 | 맵 설정을 먼저 저장해 주세요. | |
| `TEST_ROOM_UNFINISHED` | 409 | 종료되지 않은 테스트 세션이 있습니다. 테스트를 완료하거나 중단해 주세요. | `{ "roomId" }` |
| `TEST_ROOM_ALREADY_ENDED` | 409 | 이미 종료된 테스트 세션입니다. | |
| `TEST_NOT_COMPLETED` | 409 | 현재 내용으로 완료한 테스트가 없습니다. | |
| `PUBLISH_CHECK_FAILED` | 409 | 출시 전 확인할 항목이 있습니다. | `{ "issues": PublishIssue[] }` |
| `FILE_STORAGE_FAILED` | 500 | 파일 저장 중 오류가 발생했습니다. | |
| `ASSET_MANIFEST_INVALID` | 500 | 에셋 목록 파일을 읽을 수 없습니다. | |
| `CASE_CHAT_FAILED` | 502 | AI 응답을 받지 못했습니다. 초안은 변경되지 않았으며 같은 메시지로 다시 시도할 수 있습니다. | `{ "requestId" }` |
