# 개발 체크리스트 (Claude / Codex 공유용)

> 이 문서는 `project-spec.md`(전체 기획/설계)와 `docs/api/`(API 계약), `docs/conventions/`(코드 규칙)를 실제 개발 항목 단위로 쪼갠 진행상황 공유용 체크리스트입니다. 여러 에이전트가 동시에 작업하므로, 항목을 시작/완료할 때 이 파일을 직접 수정해서 상태를 갱신하세요. 각 항목의 세부 규칙은 괄호 안의 `project-spec.md` 절 번호 또는 `docs/api/` 파일을 참고하세요.
>
> 이 파일은 공개 저장소에 커밋됩니다. 사건은 **사건 A(쉬움) / 사건 B(보통) / 사건 C(어려움)** 로만 부르고, 원작 소설명·범인·트릭 등 정답 정보는 적지 않습니다. 사건과 원작의 대응은 로컬 `project-spec.md` 2.2절에만 있습니다.

## 사용 방법

- `- [ ] 항목` — 미착수
- `- [~] 항목 (담당: 이름, 메모)` — 진행 중. 작업을 시작하면 담당 에이전트 이름과 간단한 메모(막힌 점 등)를 반드시 남길 것
- `- [x] 항목 (담당: 이름)` — 완료. 구현과 필요한 검증(테스트)이 모두 끝난 경우에만 표시
- 같은 항목을 두 에이전트가 동시에 시작하지 않도록, 작업 전 이 파일을 먼저 열어 상태를 확인하고 `[~]`로 바꾼 뒤 시작합니다.
- 항목이 기획과 다르게 구현되어야 한다고 판단되면, 임의로 바꾸지 말고 `project-spec.md`를 먼저 수정(사용자와 합의)한 뒤 여기 체크리스트를 갱신하세요.
- 항목 앞의 `[W1]`~`[W7]`은 아래 주차별 계획의 목표 주차입니다. `(소유자)`는 저장소 소유자가 직접 해야 하는 작업입니다.

---

## 주차별 개발 계획 (2026-10-27 ~ 2026-12-14, 최대 7주)

- 기간: 2026-10-27(화) 시작, 7주차 종료 2026-12-14(월). 7주차는 안정화·시연 준비용 여유 기간으로 두고, 기능 개발은 6주차까지 끝내는 것을 목표로 합니다.
- 시연일은 잠정 **2026-12-14(월)**, 7주차 마지막 날입니다. 결함 수정·리허설 여유를 위해 12/11(금)까지 시연 가능한 상태를 만드는 것을 목표로 합니다. 날짜가 바뀌면 7주차 일정을 다시 맞춥니다.
- 진행 순서는 `project-spec.md` 10.1절을 따릅니다: **사건 A 1편을 개발용 시드 데이터로 끝까지 연결 → 관리자 제작 파이프라인 → 사건 B·C 제작 → 주변 기능**.
- 프론트엔드는 MSW 목으로 백엔드와 같은 주에 병렬로 개발하고, 해당 백엔드 API가 완성되면 목을 끄고 실제 서버와 연결해 확인합니다.

### 1주차 (10/27 ~ 11/02) — 프로젝트 기반과 로그인

- **목표**: 백엔드·프론트엔드가 로컬에서 실행되고, Google 로그인 → 닉네임 설정 → 메인 화면 진입까지 동작
- 백엔드: 스캐폴드, 공통 응답·예외 처리, 설정값 체계, 전체 엔티티, 인증(JWT)
- 프론트엔드: 스캐폴드, axios 공통 인스턴스(토큰 재발급), MSW, 라우팅, 로그인·닉네임 화면
- 콘텐츠·소유자: 문서 PR 병합, Google OAuth client ID·Claude API 키 준비, 사건 A 개발용 시드 작성 시작, LPC 에셋 소싱 시작
- **완료 기준**: README 명령으로 두 서버가 실행되고 실제 Google 계정으로 로그인·닉네임 설정·새로고침 후 로그인 복원이 된다

### 2주차 (11/03 ~ 11/09) — 세션·시간·맵 이동

- **목표**: 사건 카드를 골라 맵에 들어가 캐릭터를 움직이고, 물증을 조사하고, 노트를 쓰고, 일시정지·이어하기가 된다 (AI 없이)
- 백엔드: 사건 목록, 세션 생성·목록·상태·사전 정보·맵, 시작·하트비트·일시정지·재개, 서버 시간 계산·이탈 감지, 물증 조사, 개인 노트
- 프론트엔드: 메인 화면(사건 카드·내 세션 목록), 게임 화면 골격, Phaser 맵 렌더링·이동·충돌·E키 상호작용, EventBus 계약, HUD 타이머, 하트비트, 시작·이어하기 오버레이, 물증 설명 패널, 사전 정보 패널, 개인 노트(자동 저장·충돌·로컬 초안)
- 콘텐츠·소유자: 사건 A 맵(Tiled) 1차본, 플레이어·NPC 스프라이트 확보, 사건 A 개발용 시드 완성
- **완료 기준**: 사건 A 맵에서 이동·조사·노트 작성이 되고, 탭을 닫았다 다시 열면 일시정지 상태로 복원된 뒤 이어하기로 재개된다

### 3주차 (11/10 ~ 11/16) — AI 공통 인프라·NPC 대화·힌트

- **목표**: NPC에게 자유 질문을 하고, 질문형 힌트와 무료 보충 질문을 쓸 수 있다
- 백엔드: Claude API 클라이언트·사용량 로그, `AI_REQUESTS` 기반 접수·임시 확보·확정 공통 모듈, 직접 정답 요구 차단, NPC 질문·대화 기록, 힌트·보충 질문, AI 요청 상태 조회, 처리 중 요청 복구
- 프론트엔드: NPC 대화창(실패 재시도·처리 중 표시), 대화 기록 패널, 힌트 대화창(새 힌트·보충 질문 구분), `requestId` 재사용 규칙, 202 처리 중 폴링
- 콘텐츠·소유자: 사건 A NPC 응답 품질 1차 확인(설정된 거짓말·침묵 지키는지, 정답 요구 차단·정상 질문 오차단)
- **완료 기준**: AI 실패·응답 유실·중복 클릭 상황에서 대화 기록이 중복되지 않고 힌트 횟수가 잘못 차감되지 않는다(자동 테스트 포함)

### 4주차 (11/17 ~ 11/23) — 지목·채점·리빌·랭킹 → 사건 A 엔드투엔드 완성

- **목표**: 사건 A 1편을 처음부터 리빌·랭킹까지 플레이할 수 있다 (**1차 마일스톤**)
- 백엔드: 지목 제출·채점(의미 기반 판정, 난이도별 피드백), 3회 제한·재채점·마감 처리, 결과(리빌) 조회, 랭킹
- 프론트엔드: 범인 지목 창, 채점 중·채점 실패·시간 종료 채점 대기 화면, 연결 교체 안내, 리빌·결과 화면, 랭킹 페이지, 수사 기록(읽기 전용) 화면
- 콘텐츠·소유자: 사건 A 1차 수동 플레이테스트(로컬 `playtest/`에 기록), 제한시간 실측 후 난이도별 제한시간 조정
- **완료 기준**: 사건 A에서 정답(CLEARED)·3회 실패(FAILED)·시간 만료(EXPIRED) 흐름이 모두 동작하고 결과가 정확히 1건 저장된다

### 5주차 (11/24 ~ 11/30) — 관리자 사건 제작 파이프라인

- **목표**: 관리자가 원문을 올리고 AI와 채팅해 초안을 만들고 콘텐츠를 확정할 수 있다
- 백엔드: 관리자 사건 목록·상세·생성, 원문·표지 업로드, 제작 채팅(tool use로 초안 갱신), 초안 검사(`draftIssues`), 콘텐츠 확정·수정 재개, 원문 입력 구성(전체 → 대화 요약 → 원문 분할)
- 프론트엔드: 관리자 사건 목록, 사건 제작 화면(채팅 + 메모장 패널 + 확정 전 검사)
- 콘텐츠·소유자: 미리 준비해 둔 사건 B·C 원문으로 업로드량 실측(업로드 한도·입출력 예산·분할 크기·사용 모델 확정), 사건 카드 표시명·키워드 태그 확정
- **완료 기준**: 사건 A를 관리자 파이프라인으로 다시 만들어 확정할 수 있다 (또는 사건 B 초안 작성 시작)

### 6주차 (12/01 ~ 12/07) — 맵 배치·테스트·출시, 사용량 대시보드, 사건 B·C 제작

- **목표**: 사건 3편이 모두 관리자 흐름으로 출시(`PUBLISHED`)된다 (**2차 마일스톤**)
- 백엔드: 에셋 목록, 맵 설정·좌표 저장, 관리자 테스트 세션, 테스트 완료 확인, 출시 검사·출시, 복제, AI 사용량 집계·예산 설정
- 프론트엔드: 맵·테스트·출시 화면, AI 사용량 대시보드
- 콘텐츠·소유자: 사건 B·C 초안 검수·확정, 맵 제작·좌표 배치, 관리자 테스트, 표지 이미지 업로드, 출시
- **완료 기준**: 일반 사용자 화면에서 사건 3편이 보이고 각각 시작할 수 있다

### 7주차 (12/08 ~ 12/14) — 검증·안정화·시연 준비 (12/14 시연, 잠정)

- **목표**: 시연 가능한 MVP
- 사건 3편 각각 수동 플레이테스트(정상 성공·실패 종료 흐름), 발견된 결함 수정 후 재검증
- 11절 인수 기준 점검(권한·정답 비공개·오류·동시 요청·랭킹·비용 모니터링)
- README 최종 정리, 시연 환경(로컬 또는 배포) 확정 및 DB·업로드 파일 백업, 시연 리허설
- `dev` → `main` 병합
- **완료 기준**: 11절 표의 모든 영역을 확인했고, 미해결 결함이 기록되어 있다

### 일정이 밀릴 때

- 기획 범위를 임의로 줄이지 않습니다. 밀리는 항목이 생기면 이 문서에 `[~]` 메모로 남기고 저장소 소유자와 범위를 논의합니다.
- 1차 마일스톤(4주차 사건 A 엔드투엔드)을 가장 우선합니다. 관리자 파이프라인이 늦어지면 사건 B·C 제작 기간이 줄어들므로, 5주차 시작 시점에 진행 상황을 점검합니다.

---

## 0. 저장소·공통 인프라

### 저장소·문서

- [x] [W1] `dev` 브랜치 원격 푸시, 문서 브랜치 → `dev` 병합 (담당: Claude, 소유자 승인으로 PR 없이 병합)
- [x] [W1] 쓰지 않는 브랜치·worktree 정리 — 미커밋 API 보완 내용은 `dev`로 옮긴 뒤 삭제 (담당: Claude)
- [x] [W1] 루트 `.editorconfig` — UTF-8·LF·마지막 개행, Java 4칸 / TS·JSON·YAML 2칸 (common.md 포맷 규칙) (담당: BE-1)
- [x] [W1] JSON 네이밍/에러 응답/날짜 형식 등 API 공통 컨벤션 확정 — 초안은 `docs/api/README.md`·`errors.md`에 작성됨 (메모: FE-2·BE-3 구현과 INT로 확인)
- [~] [W1] Google Cloud OAuth client ID 발급, Claude API 키 발급 (소유자, 메모: OAuth client ID 확인(INT), Claude API 키는 미확인)

### 백엔드 기반

- [x] [W1] Spring Boot 스캐폴드 — Java 21, Spring Boot 4.1.0, Gradle Wrapper 커밋, 루트 패키지 `com.shelved` (`common`/`infra`/`domain` 구조, backend.md) (담당: BE-1)
- [x] [W1] 의존성 — Web, Validation, Data JPA, PostgreSQL, Security, springdoc-openapi, Lombok, Testcontainers (담당: BE-1, BE-3)
- [x] [W1] PostgreSQL 로컬 실행 환경 (`docker-compose.yml`) (담당: BE-1)
- [x] [W1] 환경별 프로필 — 테스트 `create-drop`, 폐기 가능한 로컬 `update`, 보존할 개발·시연 DB `validate` (backend.md 환경별 스키마 관리) (담당: BE-1)
- [x] [W1] `application.yml` + `@ConfigurationProperties` (`common/config`, 키 `shelved.{영역}.{항목}`) — 난이도별 제한시간·힌트 횟수, 지목 기회(3), 하트비트 간격(5s)·이탈 판정(15s), AI 작업 시간 제한, 채점 재시도 최소 간격(10s), 노트 최대 글자 수, 원문 업로드 크기 한도, 표지 크기 한도(5MB), Claude 모델명(본 응답용·질문 분류용 `shelved.llm.filter-model`), 입·출력 토큰 예산, 에셋 경로(`shelved.assets.base-path`), 표지 저장 경로 (담당: BE-1)
- [~] [W1] 잠정 초기값 적용 (사건 A 플레이테스트 후 조정) — 하트비트 간격 5초·이탈 판정 15초, 채점 재시도 최소 간격 10초, 노트 최대 5000자·입력 멈춘 뒤 2초 후 자동 저장, 게임 AI 호출 시간 제한 60초, 관리자 제작 채팅 시간 제한 300초, `PROCESSING` 복구 기준은 시간 제한 + 60초, AI 요청 상태 조회 간격 2초 (담당: BE-1, 서버 설정값만 적용. 자동 저장 2초·상태 조회 간격 2초는 프론트엔드 값)
- [x] [W1] `Clock` 빈 등록 (게임 시간 판정용, backend.md 12) (담당: BE-1)
- [x] [W1] `CommonResponse`, `ErrorData`, `PageResponse`, `SuccessMessage`, `ValidationMessage` (담당: BE-1)
- [x] [W1] `ErrorCode` — `docs/api/errors.md`의 전체 코드·상태·메시지를 그대로 정의 (담당: BE-1)
- [x] [W1] 공통 입력 검증 — 문자열은 앞뒤 공백 제거 후 검증(개인 노트 `content`는 예외로 원문 보존), 길이는 UTF-16 코드 단위, 숫자 ID는 양의 정수·페이지·횟수·버전은 0 이상의 정수·좌표는 정수 픽셀, 위반 시 본문 `INVALID_INPUT` / 경로·쿼리 `INVALID_PARAMETER` (`docs/api/README.md` 공통 규칙) (담당: BE-1, 공통 처리 완료. 필드별 범위 제약(`@Positive` 등)은 각 DTO 작성 시 추가)
- [x] [W1] `CustomException(ErrorCode)` / `CustomException(ErrorCode, detail)`, `GlobalExceptionHandler` — `@Valid` 실패, 본문 파싱 오류, 지원하지 않는 메서드, 타입 불일치, 외부 API 실패, 기타 런타임 예외를 공통 응답으로 변환 (담당: BE-1)
- [x] [W1] 인증 필터 오류 처리 — `AuthenticationEntryPoint`·`AccessDeniedHandler`·JWT 필터에서도 같은 `CommonResponse.exception(...)` 형식 반환 (담당: BE-3)
- [x] [W1] springdoc Swagger UI 로컬 노출 (담당: BE-1)
- [x] [W1] 로그 정책 — 토큰·API 키·인증 헤더·원문·프롬프트·정답·노트 전문을 로그에 남기지 않도록 HTTP 클라이언트·SQL 바인딩 디버그 로그 설정 확인 (담당: BE-1)
- [x] [W1] `backend/.env.example` — DB 접속 정보, `GOOGLE_CLIENT_ID`, JWT 서명 키, `CLAUDE_API_KEY`, `CLAUDE_MODEL`, `CLAUDE_FILTER_MODEL` (가짜 값만) (담당: BE-1)

### 프론트엔드 기반

- [x] [W1] Vite + React + TypeScript 스캐폴드, `frontend/.nvmrc`(Node LTS), `package-lock.json` 커밋 (담당: FE-1)
- [x] [W1] Tailwind CSS(디자인 기준값은 설정 파일에), ESLint + Prettier, Vitest + React Testing Library (담당: FE-1)
- [x] [W1] npm 스크립트 — `lint`, `typecheck`, `test -- --run`, `build` (담당: FE-1)
- [x] [W1] React Router, TanStack Query, Zustand 설정 (`src/app`) (담당: FE-1)
- [x] [W1] 폴더 구조 — `app`/`pages`/`features/{도메인}`/`game`/`mocks`/`shared` (frontend.md) (담당: FE-1)
- [x] [W1] `shared/api/client.ts` — 기본 주소, 액세스 토큰 첨부, `401 EXPIRED_TOKEN`일 때만 재발급 후 1회 재전송(동시 만료 시 재발급 Promise 공유), `X-Connection-Id` 헤더 첨부, 실패를 HTTP 상태·`message`·`data.code`·`data.detail` 보존 공통 에러로 변환 (담당: FE-1, FE-2)
- [x] [W1] `shared/api/types.ts` — `CommonResponse<T>`, `ErrorData`, `PageResponse<T>`, 공통 enum 타입 (`docs/api/README.md` 공통 enum) (담당: FE-1)
- [x] [W1] MSW 설정 — `VITE_USE_MOCK=true`일 때만 동작, 배포 빌드 제외, `src/mocks/handlers/{도메인}.ts`, 가상 사건 데이터만 사용 (담당: FE-1)
- [x] [W1] `frontend/.env.example` — `VITE_API_BASE_URL`, `VITE_GOOGLE_CLIENT_ID`, `VITE_USE_MOCK` (담당: FE-1)

### README

- [x] [W1] README에 기술 버전, 실행 명령(작업 디렉터리 포함), 필수 환경 변수 이름·용도, `.env` 주입 방법, DB 초기화 방법 기록 (common.md README 관리) (담당: BE-1, FE-1)
- [~] [W2] README에 정적 에셋 경로, 로그인 토큰 전달·보관·갱신 방식 기록 (담당: FE-2, 메모: 토큰 방식 기록 완료, 정적 에셋 경로는 2주차)

## 1. 데이터베이스 (엔티티, 6절)

> 엔티티는 `common/entity`, ID는 `Long` + `IDENTITY`, enum은 `EnumType.STRING`, 긴 문자열은 `text`, jsonb는 전용 record 타입 (backend.md 엔티티 규칙)

- [x] [W1] 공통 enum — `UserRole`, `Difficulty`, `CaseStatus`, `RoomStatus`, `AiRequestType`, `AiRequestStatus`, `GradingStatus`, `Verdict`, `MessageSenderType`, `AdminMessageSenderType`, `HotspotType`, `LlmRequestType` (담당: BE-1, BE-2)
- [x] [W1] `USERS`(닉네임 최초 null, **영문 대소문자를 구분하지 않는 UNIQUE**), `REFRESH_TOKENS`(해시 저장, `revoked_at`) (6.1절). 소문자로 정규화한 `nickname_lower` 컬럼(`Locale.ROOT`)을 함께 저장하고 이 컬럼에 UNIQUE (담당: BE-1)
- [x] [W1] `CASES` — 상태, 카드 정보, 정답 보호 필드, 사전 정보, 리빌 해설, `glossary`(jsonb), `cloned_from_case_id`, `content_updated_at`, `test_verified_at`, `chat_context_revision`(0부터) (6.2절) (담당: BE-2)
- [x] [W1] `CASE_SOURCE_TEXTS`(원문 전문 `text`), `CASE_DRAFTS`(`draft_json`, `docs/api/admin-draft-schema.md` 구조), `ADMIN_CASE_MESSAGES`(`request_id`, `tool_changes`·`input_info` jsonb, `chat_context_revision`, `response_json`(관리자 발화에 전체 응답 스냅샷, DTO 비노출), `status`, `error_message`), `CASE_MAPS`(`case_id` UNIQUE) (6.3절) (담당: BE-2)
- [x] [W1] `SUSPECTS`(`persona_prompt` text, `sprite_key`, `is_culprit`, `can_converse`), `EVIDENCES`, `CASE_TRICK_ELEMENTS`(`grading_rubric` jsonb), `MAP_HOTSPOTS`(`type` + `ref_id` 폴리모픽 참조) (6.2절) (담당: BE-2)
- [x] [W1] `ROOMS`(`is_test`, `status`, `time_limit_sec`, `started_at`, `elapsed_active_ms`, `active_since`, `last_seen_at`, `connection_id`), `ROOM_PARTICIPANTS` (6.4절) (담당: BE-2)
- [x] [W1] `NPC_CONVERSATIONS`, `NPC_MESSAGES`(`(conversation_id, sequence_no)` UNIQUE) (담당: BE-2)
- [x] [W1] `PLAYER_NOTES`(`(room_id, user_id)` UNIQUE, `revision`) (담당: BE-2)
- [x] [W1] `HINTS`, `HINT_FOLLOWUPS`(`hint_id` UNIQUE) (담당: BE-2)
- [x] [W1] `ACCUSATIONS`(`submitted_at`, `submitted_active_ms`, `grading_status`, `request_id`, `element_results` jsonb, `feedback`), `PLAY_RESULTS`(`room_id` UNIQUE) (담당: BE-2)
- [x] [W1] `AI_REQUESTS`(`(room_id, request_id)` UNIQUE, `input_json` jsonb, `result_json` jsonb(플레이어용 성공 응답 스냅샷, 내부 프롬프트 제외), `execution_no`, `superseded_at`, `result_ref_id`, `error_code`) (담당: BE-2)
- [x] [W1] `EVIDENCE_INSPECTIONS`(`(room_id, evidence_id)` UNIQUE) (담당: BE-2)
- [x] [W1] `LLM_USAGE_LOGS`(`usage_known`, 미확인 시 토큰·비용 null), `LLM_BUDGET_SETTINGS`(단일 설정) (6.5절) (담당: BE-2)
- [x] [W1] UNIQUE 제약이 엔티티에 모두 선언되었는지 확인 (서비스 중복 검사로 대신하지 않음) (담당: BE-2)
- [x] [W1] 커밋용 가상 샘플 사건 데이터 (예: 「시계탑 저택의 마지막 종소리」) — 테스트 픽스처·MSW 목에서 공유 (담당: BE-2, 메모: 백엔드 테스트 픽스처 `ClocktowerCaseFixture`. MSW 목은 2주차)
- [ ] [W1~W2] 사건 A 개발용 시드 데이터 작성 — 로컬 전용 `backend/src/main/resources/dev-data/`, 커밋 금지, 카드 표시명·태그는 개발용 임시 이름 (소유자 검수)
- [ ] [W2] 로컬 프로필에서 시드를 읽어 `PUBLISHED` 사건으로 넣는 로더

## 2. 인증 (7.1절, 6.1절, `docs/api/auth-user.md`)

- [x] [W1] `POST /api/auth/google` — Google ID 토큰 검증(발급자·대상 client ID·만료), `google_sub`로 사용자 조회·생성(`role=PLAYER`, `nickname=null`) (담당: BE-3, 구현·대체 구현 테스트 완료, 실제 Google 로그인 확인 완료(INT))
- [x] [W1] JWT 액세스 토큰 발급(1시간)/검증 필터, 만료 시 `401 EXPIRED_TOKEN`, 위조 시 `401 INVALID_TOKEN` (담당: BE-3)
- [x] [W1] 리프레시 토큰 발급(2주)·해시 저장, `POST /api/auth/refresh` — 재발급 시 rotation(이전 토큰 무효화와 새 토큰 저장을 한 트랜잭션에서 원자적으로, 같은 토큰 중복 rotation 금지), 실패 시 `401 INVALID_REFRESH_TOKEN` (담당: BE-3)
- [x] [W1] `POST /api/auth/logout` — 리프레시 토큰 무효화, 이미 무효·없는 토큰도 `200` (담당: BE-3)
- [x] [W1] `GET /api/users/me` — 내 정보 조회 (새로고침 후 로그인 복원) (담당: BE-3)
- [x] [W1] `PATCH /api/users/me/nickname` — 2~12자 완성형 한글(`가-힣`)·영문·숫자, 영문 대소문자 무시 중복 검사(표시는 입력 대소문자 보존), 본인 기존 닉네임으로 변경 허용, 중복 시 `409 NICKNAME_EXIST` (담당: BE-3)
- [x] [W1] 닉네임 미설정 사용자는 로그인·내 정보·닉네임 API 외 `403 NICKNAME_REQUIRED` (담당: BE-3)
- [x] [W1] 인증 불필요 API 허용 — 로그인·재발급·로그아웃, `GET /api/cases`, `/uploads/covers/**` (담당: BE-3)
- [x] [W1] 관리자 `role` 검사 인가 로직 (`/api/admin/**`, 아니면 `403 ACCESS_DENIED`) (담당: BE-3)
- [x] [W1] 관리자 계정 수동 생성 절차 README에 문서화 (DB에서 직접 `role='ADMIN'`) (담당: BE-3)
- [x] [W1] 테스트 — 토큰 만료·위조, 재발급 rotation, 같은 리프레시 토큰 동시 재발급 시 한 번만 성공, 로그아웃 후 재발급 거부, 닉네임 중복(대소문자만 다른 경우 포함)·검증 (담당: BE-3)

## 3. 사건 조회 & 랭킹 (7.2절, 3.10절, `docs/api/cases-ranking.md`)

- [ ] [W2] `GET /api/cases` — `PUBLISHED`만, 비로그인 허용, 정답 필드 미포함, `timeLimitSec`·`hintLimit`·`accusationLimit`은 설정값
- [ ] [W4] `GET /api/rankings/{caseId}` — `cleared=true` & `is_test=false`만, 사용자별 최소 `durationSec` 1건, 공동 순위(1·2·2·4), 같은 순위는 `clearedAt` 순, `myRanking`, 페이지네이션, 비공개 사건 `404 CASE_NOT_FOUND`
- [ ] [W4] 테스트 — 실패·만료·테스트 세션 제외, 재도전 최고 기록 갱신, 공동 순위, 힌트 횟수 무관

## 4. 게임 세션 라이프사이클 (3.5절, 6.4절, 7.3절, `docs/api/room.md`)

- [ ] [W2] 세션 참가자 검사 공통 로직 — 본인 세션이 아니면 `404 ROOM_NOT_FOUND`, 테스트 세션은 관리자만
- [ ] [W2] `POST /api/rooms` — `PUBLISHED` 사건만, 같은 사건 미종료 세션이 있으면 `409 ROOM_ALREADY_EXISTS`(`detail.roomId`), 제한시간은 난이도 설정값 복사, 빈 노트(`revision=0`) 함께 생성
- [ ] [W2] `GET /api/rooms` — `status=ACTIVE|ENDED` 필터, 테스트 세션 제외, 최신순, `pendingGradingStatus`·`endedAt`·`durationSec` 포함
- [ ] [W2] `GET /api/rooms/{roomId}/state` — `timer`, `heartbeatIntervalMs`, `hintCount`·`accusationCount`(`UsageCount`: limit/used/reserved/remaining), `pendingAccusation`, `aiRequests`, `conversations`
- [ ] [W2] `GET /api/rooms/{roomId}/briefing` — 사전 정보, 정답·용어집 제외
- [ ] [W2] `GET /api/rooms/{roomId}/map` — 맵 설정, `suspects`(배치 안 된 인물은 x·y null, 지목 대상 목록 겸용), `evidences`(설명 제외)
- [ ] [W2] 서버 경과시간 계산 — `elapsed_active_ms + (now - active_since)`, `Clock` 주입, `duration_sec`는 초 단위 내림
- [ ] [W2] `POST /api/rooms/{roomId}/start` — `READY`만(`409 ROOM_NOT_READY`), `started_at`·`active_since` 설정, `connectionId` 발급
- [ ] [W2] `X-Connection-Id` 검사 공통 로직 — 없으면 `400 CONNECTION_ID_REQUIRED`, 현재 연결 아니면 `409 CONNECTION_REPLACED` (적용 대상은 `docs/api/README.md` 표)
- [ ] [W2] `POST /api/rooms/{roomId}/heartbeat` — `last_seen_at` 갱신, `IN_PROGRESS`가 아니면 연결 검사 없이 상태만 반환, 자동 재개 금지
- [ ] [W2] `POST /api/rooms/{roomId}/pause` — `IN_PROGRESS`일 때만 상태 변경, 그 외 상태는 현재 상태 반환(중복 안전), 연결 헤더 불필요, 현재 연결 무효화. 시간 정산 순서는 `docs/api/room.md`의 "일시정지의 시간 정산 순서"를 따름: `pause`는 `last_seen_at`을 갱신하지 않고 먼저 정산 → 이탈 기준 이상이면 마지막 접속 확인 시각까지, 미만이면 `pause` 접수 시각까지 누적 → 시간 소진 시 미해결 지목이 없으면 `EXPIRED`, 있으면 보류한 채 `PAUSED`
- [ ] [W2] `POST /api/rooms/{roomId}/resume` — `PAUSED`만(`409 ROOM_NOT_PAUSED`), 남은 시간 없으면 `409 ROOM_TIME_EXPIRED`, 새 `connectionId` 발급
- [ ] [W2] 유효한 플레이 요청도 `last_seen_at` 갱신 (접속 확인으로 취급)
- [ ] [W2] 이탈 감지 스케줄러 — 15초 미수신 시 마지막 접속 확인 시각 기준으로 일시정지, AI 대기 요청 때문에 막히지 않음 (`scheduler` 패키지는 Service 호출만)
- [ ] [W2] 접속 종료·시간 만료 순서 처리 — 하트비트 유예 구간에서는 만료 보류, 접속 확인 시점에 이미 소진했으면 만료, 일시정지 중 만료 금지 (3.5절)
- [ ] [W2] 세션 상태 변경 직렬화 — `rooms` 행 비관적 잠금(`PESSIMISTIC_WRITE`), 시간 중복 누적·결과 중복 생성 방지 (backend.md 15)
- [ ] [W4] 시간 소진 시 종료 처리 — 유효한 미해결 지목이 없으면 `EXPIRED`로 종료·결과 저장, 있으면 `awaitingGrading=true`로 보류
- [ ] [W4] `GET /api/rooms/{roomId}/result` — 종료 세션만(`409 ROOM_NOT_ENDED`), 원작·범인·트릭·리빌 해설·지목 기록·플레이 기록
- [ ] [W2~W4] 테스트 — 상태 전이 거부, 일시정지 구간 제외, 연결 교체 후 이전 연결 거부, 이탈 판정, 이탈 기준 직전·직후의 pause 정산, 중복 pause·resume, 일시정지 중 만료 금지, 결과 1건 (고정 `Clock`)

## 5. AI 요청 공통 인프라 (3.6절, 6.4~6.5절, backend.md AI 연동 규칙)

- [ ] [W3] `infra/llm` Claude API 클라이언트 — 모델명·키 설정값, 호출 시간 제한, 응답 사용량 추출
- [ ] [W3] 테스트용 Fake LLM 클라이언트 — 지연·오류·형식 불일치 재현
- [ ] [W3] `LLM_USAGE_LOGS` 기록 — 성공·실패 무관 호출마다, 재시도 포함, `request_type`·`call_status`·`error_code`, 게임 호출은 `room_id`·`case_id`, 사용량 미확인 시 `usage_known=false`·null
- [ ] [W3] 프롬프트 파일 관리 — `src/main/resources/prompts/` (Java 문자열에 긴 프롬프트 금지)
- [ ] [W3] `AI_REQUESTS` 접수 서비스 — 같은 `requestId` + 같은 입력이면 기존 결과·처리 중 상태 반환, 입력 다르면 `409 AI_REQUEST_ID_REUSED`, 대상별 동시 1건(`409 AI_REQUEST_IN_PROGRESS`), 한도 = 완료 + 처리 중 임시 확보
- [ ] [W3] 짧은 트랜잭션 분리 — 접수(임시 확보) → 트랜잭션 밖 AI 호출 → 결과 확정 (backend.md 13)
- [ ] [W3] 결과 확정 — 정상 결과·게임 데이터 저장과 `SUCCEEDED`·횟수 사용을 한 트랜잭션에서 확정, 실패 시 `FAILED`·임시 확보 해제·`502` 유형별 코드
- [ ] [W3] 완료 결과 스냅샷 — 최초 성공 응답을 `AI_REQUESTS.result_json`에 저장하고, 같은 `requestId` 재전송·상태 조회 시 당시 값 그대로 반환(남은 횟수·세션 상태를 현재 값으로 재계산하지 않음, 서버 재시작 후에도 복원)
- [ ] [W3] 재시도 — 같은 `requestId`·입력으로 재실행 시 `execution_no` 증가, 이전 실행의 늦은 응답은 게임 데이터·횟수에 미반영
- [ ] [W3] 최종 종료 후 도착한 응답은 기록·횟수에 미반영, `409 ROOM_ENDED_DURING_REQUEST`, 사용량 로그만 기록 (일시정지는 정상 저장)
- [ ] [W3] 처리 중 `202 Accepted` + `AiRequestStatusResponse` 반환
- [ ] [W3] `GET /api/rooms/{roomId}/ai-requests/{requestId}` — 참가자 권한 검사, 일시정지·종료 후에도 조회, 내부 입력 컨텍스트 미노출
- [ ] [W3] 작업 시간 제한·서버 재시작 복구 — 오래된 `PROCESSING`을 `FAILED`로 정리하는 스케줄러·시작 시 복구
- [ ] [W3] 직접 정답 요구 차단 모듈 — NPC 질문·새 힌트·보충 질문마다 작은 모델(`shelved.llm.filter-model`)로 의도 분류 호출(단순 금칙어 금지), AI 요청 접수·기회 확보 전에 수행, 차단 질문은 기록하지 않음(`400 QUESTION_BLOCKED`), 분류 호출은 `QUESTION_FILTER`로 사용량 기록, 분류 실패·형식 오류 시 접수하지 않고 해당 API의 `502` 반환(기회 미차감)
- [ ] [W3] 분류 프롬프트·판정 예시 — 차단: 범인 정체·범행 여부를 직접 또는 우회적으로 묻는 질문 / 허용: 행적·목격 사실·물증 질문, 증거 기반 추궁 (가상 사건 예시로 단위 테스트)
- [ ] [W3] 테스트 — 같은 `requestId` 재전송 시 같은 결과(이후 횟수가 바뀌어도 당시 스냅샷), 동시 요청 한도 초과·중복 저장 없음(Testcontainers 통합 테스트), AI 실패 시 미차감, 늦은 응답 미반영, 재시작 복구

## 6. NPC 대화 (2.4절, 3.7절, `docs/api/investigation.md`)

- [ ] [W3] `POST /api/rooms/{roomId}/npc/{suspectId}/ask` — 세션 상태 공통 검사, 이 사건의 인물인지(`404 SUSPECT_NOT_FOUND`), `canConverse=false`면 `400 NPC_NOT_CONVERSABLE`, 질문 1~500자, 질문·답변을 `sequence_no` 순서로 저장, 실패 시 `502 NPC_ANSWER_FAILED`
- [ ] [W3] 이미 완료된 같은 `requestId`는 세션 상태 검사 없이 저장된 결과 반환
- [ ] [W3] NPC 시스템 프롬프트 구성 — `persona_prompt` + 난이도별 응답 방식(쉬움: 자진해서 살짝 덧붙임 / 보통: 친절하게, 묻지 않은 것은 말하지 않음 / 어려움: 물어본 것만) + 용어집 + 해당 NPC 대화 이력 (2.3절)
- [ ] [W3] 원작 기반 응답 원칙 반영 — 인물이 알 수 없는 사실 답변 금지, 설정에 없는 사건 사실·거짓말 즉흥 생성 금지, 증거 기반 추궁 허용·자백 여부는 설정 따름 (2.4절)
- [ ] [W3] `GET /api/rooms/{roomId}/npc/{suspectId}/messages` — `sequenceNo` 순, `pendingRequests` 포함, 모든 상태에서 조회
- [ ] [W3] 테스트 — 대화 불가 인물 거부, 다른 사건 인물 거부, 차단 질문 미기록, NPC별 동시 1건

## 7. 물증 (3.8절, `docs/api/investigation.md`)

- [ ] [W2] `POST /api/rooms/{roomId}/evidences/{evidenceId}/inspect` — 세션 상태 공통 검사, 이 사건의 물증인지(`404 EVIDENCE_NOT_FOUND`), 검수된 고정 설명 반환(AI 미사용), 반복 조사 가능
- [ ] [W2] 최초 조사 이력 `EVIDENCE_INSPECTIONS` 1건만 저장 (중복 요청에도 1건, 힌트 컨텍스트용)
- [ ] [W2] 테스트 — 재조사 같은 설명, 이력 1건, 진행 중 아닐 때 거부

## 8. 개인 노트 (3.7절, `docs/api/investigation.md`)

- [ ] [W2] `GET /api/rooms/{roomId}/notes/me` — `content`, `revision`, `updatedAt`, `maxLength`, `editable`(진행 중이고 시간이 남을 때만 true)
- [ ] [W2] `PUT /api/rooms/{roomId}/notes/me` — 세션 상태 공통 검사, 글자 수 제한, `content`는 trim·공백·줄바꿈 정규화 없이 원문 보존, `revision` 일치 시에만 저장·버전 증가, 불일치 시 `409 NOTE_REVISION_CONFLICT`(`detail.currentRevision`, `currentContent`)
- [ ] [W2] 테스트 — 오래된 `revision` 거부, 다른 사용자 노트 접근 차단, 일시정지·종료 후 저장 거부

## 9. 힌트 & 무료 보충 질문 (3.4절, `docs/api/hint-accusation.md`)

- [ ] [W3] `GET /api/rooms/{roomId}/hints` — 힌트·보충 질문 시간순, `hintCount`, `followupAvailable`, `pendingRequests`, 종료 후에도 조회
- [ ] [W3] `POST /api/rooms/{roomId}/hints` — 난이도별 한도(쉬움 5/보통 3/어려움 1, 처리 중 확보분 포함), 세션당 동시 1건, 정상 답변 저장 시에만 1회 사용, 실패 시 `502 HINT_GENERATION_FAILED`
- [ ] [W3] `POST /api/rooms/{roomId}/hints/{hintId}/followup` — 이 세션의 힌트인지(`404 HINT_NOT_FOUND`), 힌트당 1회(`409 HINT_FOLLOWUP_ALREADY_USED`), 힌트 횟수 0이어도 가능, 새 정보 요구 시 안내 답변도 1회 사용 처리
- [ ] [W3] 힌트 프롬프트 — 플레이어 질문, NPC 대화, 물증 조사 이력, 이전 힌트, 사건 정보 참고. 범인·트릭 정답 직접 공개 금지, NPC 진술의 출처 유지(거짓일 수 있는 진술을 사실처럼 말하지 않음)
- [ ] [W3] 보충 질문 프롬프트 — 원래 질문·힌트 답변 범위 안에서만 풀어 설명, 새 단서·정답 정보 추가 금지
- [ ] [W3] `HINT_FOLLOWUP` 유형으로 사용량 기록
- [ ] [W3] 테스트 — 한도 소진, 동시 요청 한도 초과 없음, 보충 질문 중복 거부, 차단 질문 미차감, 실패 미차감

## 10. 지목 & 채점 (3.2절, 3.5~3.6절, `docs/api/hint-accusation.md`)

- [ ] [W4] `GET /api/rooms/{roomId}/accusations` — 지목 기록과 남은 횟수
- [ ] [W4] `POST /api/rooms/{roomId}/accusations` (새 답안) — 연결 헤더 필수, `IN_PROGRESS`·남은 시간·남은 기회·채점 중 지목 없음 확인, 트릭 설명 10~3000자, 접수 시각·`submitted_active_ms` 저장
- [ ] [W4] 채점 프롬프트 — `grading_rubric` 항목별 `MET`/`MISSING`/`INCORRECT`와 답안 속 근거·이유를 구조화 출력, 제출 답안만 평가(이전 제출·수집 단서로 채우지 않음), 반대 가설 나열은 미충족
- [ ] [W4] 채점 결과 형식 검증 — 필수 항목 누락·형식 오류는 채점 실패(`ERROR`)로 처리, 오답 아님
- [ ] [W4] 최종 판정 서버 계산 — 범인 틀림 → 오답 / 범인 맞음 + 전부 충족 → 정답 / 일부 충족 → 부분 정답 / 하나도 없음 → 오답
- [ ] [W4] 난이도별 `feedback` 생성 — 모든 난이도에서 맞힌 부분·누락·오류 구분. 쉬움: 보완 질문과 참고할 단서·NPC까지 안내(`easyFeedback`) / 보통: 보완할 항목 안내, 구체적 조사 대상은 제시하지 않음 / 어려움: 맞힌 부분과 누락·오류의 이유만, 정답 방향·조사 대상 제시 금지. 내부 정답 기준 전체는 응답에 포함하지 않음
- [ ] [W4] 결과 반영 — 정답 → `CLEARED`(기록 시간은 접수 시점 누적 시간), 3회째 미정답 → `FAILED`, 시간 소진 → `EXPIRED`, 그 외 수사 계속. `PLAY_RESULTS` 정확히 1건
- [ ] [W4] 채점 실패 재시도 — 같은 `requestId`·같은 답안, 연결 헤더 불필요, 미종료 세션(마감 후 포함), 최소 10초 간격(`429 GRADING_RETRY_TOO_SOON`), 기회 미차감, 원래 접수 시점 유효성 유지
- [ ] [W4] 마감 전 새 답안 제출 시 이전 실패 지목 `superseded` 처리(`409 AI_REQUEST_SUPERSEDED`), 마감 후 새 답안 거부(`409 ROOM_TIME_EXPIRED`)
- [ ] [W4] 채점 대기 중 시간 만료 시 결과 확정 보류(`awaitingGrading`), 결과 도착 후 종료 처리
- [ ] [W4] 테스트 — 제한시간 직전·직후 제출, 채점 중 시간 만료, 오류 후 재채점, 3회 소진, 대체된 지목 재채점 거부, 결과 1건, 힌트 미차감

## 11. 관리자 — 사건 제작 파이프라인 (4.1~4.2절, 4.5절, `docs/api/admin-casebuilder.md`, `admin-draft-schema.md`)

- [ ] [W5] 사건 상태·잠금 공통 검사 — `CASE_PUBLISHED_LOCKED`, `CASE_NOT_DRAFT`, `CASE_NOT_CONTENT_CONFIRMED`, `CASE_CHAT_IN_PROGRESS`, `TEST_ROOM_UNFINISHED`, `CASE_AI_REQUEST_IN_PROGRESS` (모든 수정 경로에서 서버 검사)
- [ ] [W5] `GET /api/admin/cases` — 상태 필터, 최근 수정순, `testVerified`·`unfinishedTestRoomCount`
- [ ] [W5] `POST /api/admin/cases` — 빈 초안(`schemaVersion: 1`)을 가진 `DRAFT` 생성
- [ ] [W5] `GET /api/admin/cases/{caseId}` — 초안·`draftIssues`·확정 콘텐츠 ID 목록·맵·배치·`chatProcessing` 복원
- [ ] [W5] `GET /api/admin/cases/{caseId}/messages` — 채팅 이력, `toolChanges`, `inputInfo`, 실패 시 `errorMessage`
- [ ] [W5] `POST /api/admin/cases/{caseId}/source-text` — `.txt`·UTF-8·빈 파일 검사, 크기 한도, 전문 그대로 저장, 교체 허용(`DRAFT`)
- [ ] [W5] `POST /api/admin/cases/{caseId}/cover-image` — jpg/png/webp·5MB, 로컬 디스크 저장(무작위 파일명), `/uploads/covers/{파일명}` 정적 제공, 이전 파일 삭제(복제본과 공유 시 유지)
- [ ] [W5] 제작 채팅 tool 정의 (`casebuilder` 도메인) — `set_card_info`, `set_source_info`, `set_briefing`, `add/update/remove_suspect`, `add/update/remove_evidence`, `set_trick_summary`, `add/update/remove_trick_element`, `set_reveal_explanation`, `set_glossary_term`, `remove_glossary_term`
- [ ] [W5] tool 실행기 — 메모리 사본에 적용, 키(`sus-N`/`evi-N`/`trk-N`/`trk-N-iM`) 서버 발급·재사용 금지, 인자 검증 실패는 해당 tool 오류 `tool_result`로 반환, 응답 완료 시 대화·초안 한 번에 저장
- [ ] [W5] `POST /api/admin/cases/{caseId}/chat` — `requestId`는 사건 안에서 유일, 사건당 동시 1건, 원문 필수(`409 SOURCE_TEXT_REQUIRED`), 실패 시 초안 유지(`502 CASE_CHAT_FAILED`), 응답에 전체 초안·`draftIssues`
- [ ] [W5] 제작 채팅 재시도 기준 — `chat_context_revision`은 원문 업로드·교체, 새 채팅 성공 저장, 확정·수정 재개마다 +1(표지·맵·좌표 변경은 제외). 접수 시 버전을 관리자 발화에 저장하고, 실패 요청은 `DRAFT`이고 버전이 같을 때만 같은 `requestId`로 재실행, 다르면 `409 CASE_CHAT_CONTEXT_CHANGED`(`detail.currentRevision`). 재시도는 기존 관리자 발화 상태만 갱신(중복 발화 금지), 실패한 발화는 생성용 대화 이력에서 제외
- [ ] [W5] 완료 채팅 재전송 — 관리자 발화의 `response_json` 스냅샷 반환(확정·출시 후에도, AI 재호출 없음). 같은 ID의 입력 비교·완료 조회는 사건 수정 가능 상태 검사보다 먼저 수행
- [ ] [W5] 원문 입력 구성 — 원문·시스템 지시·대화 이력·초안·용어집·tool 정의·출력 여유 합산 → 초과 시 오래된 대화 요약 → 그래도 초과 시 원문 장·문단 분할. 맞출 수 없으면 `400 LLM_INPUT_TOO_LARGE`, 구성 방식은 `inputInfo`로 저장·반환 (8.4절)
- [ ] [W5] 초안 검사(`DraftIssue` ERROR/WARNING) — `admin-draft-schema.md` 검사 표 전체, "문자열·배열·숫자 제약" 표의 상한(이름 1~100자, 키워드 1~5개, 트릭 요소 최대 5개, `rubric.items` 1~20개 등)과 `orderIndex` 중복 금지, 대화 불가 인물의 `persona`·`spriteKey`는 `null` 허용
- [ ] [W5] `POST /api/admin/cases/{caseId}/confirm` — ERROR 있으면 `409 DRAFT_INVALID`, `persona` → `persona_prompt` 템플릿 변환, `rubric` 키 → 엔티티 ID 변환, 재확정 시 `entityId` 있는 항목은 같은 행 갱신·삭제 항목 행 삭제, 무효 배치 제거(`removedHotspots`), `content_updated_at` 갱신·`test_verified_at` 초기화
- [ ] [W5] `POST /api/admin/cases/{caseId}/reopen` — 미종료 테스트 거부, 종료된 테스트 세션과 종속 데이터 삭제(채팅·초안·사용량 로그 유지), `deletedTestRoomCount` 반환
- [ ] [W5] 서버 재시작 시 처리 중 제작 채팅을 실패로 복구
- [ ] [W5] 테스트 — tool 인자 오류 처리, 키 재사용 금지, 실패 시 초안 불변, 재확정 ID 보존, 공개 사건 수정 차단, 원문 교체·다른 채팅 성공 후 과거 실패 요청 재시도 시 `CASE_CHAT_CONTEXT_CHANGED`, 완료 요청 재전송 시 당시 스냅샷

## 12. 관리자 — 맵 & 출시 (4.4절, 4.6절, `docs/api/admin-casebuilder.md`)

- [ ] [W6] `GET /api/admin/assets` — 맵 파일·레이어·`spriteKey` 선택 목록 (`manifest.json` 기준, 읽기 실패 시 `500 ASSET_MANIFEST_INVALID`)
- [ ] [W6] `PUT /api/admin/cases/{caseId}/map` — 시작 위치/충돌 레이어/상호작용 반경, 맵 파일 로드·레이어 존재·시작점 범위·이동 불가 영역 검사(`400 MAP_INVALID`)
- [ ] [W6] `PUT /api/admin/cases/{caseId}/hotspots` — 전체 교체, 다른 사건·없는 대상·중복·맵 밖·이동 불가 영역 검사(`400 HOTSPOT_INVALID`), 맵 설정 전 `409 MAP_NOT_CONFIGURED`
- [ ] [W6] 맵·좌표 변경 시 `content_updated_at` 갱신·`test_verified_at` 초기화 (표지 변경은 제외)
- [ ] [W6] 출시 전 자동 검사 로직 — `CARD_INFO_MISSING`, `CONTENT_MISSING`, `MAP_MISSING`, `SPAWN_INVALID`, `HOTSPOT_MISSING`, `HOTSPOT_INVALID`, `SPRITE_INVALID`, `TEST_UNFINISHED`, `TEST_NOT_VERIFIED`
- [ ] [W6] `GET /api/admin/cases/{caseId}/publish-check` — 출시 검사 미리보기 (출시 API와 같은 검사)
- [ ] [W6] `POST /api/admin/cases/{caseId}/publish` — `CONTENT_CONFIRMED → PUBLISHED`, 실패 시 `409 PUBLISH_CHECK_FAILED`
- [ ] [W6] `POST /api/admin/cases/{caseId}/clone` — 공개 사건만, 원문·초안·확정 콘텐츠·표지·맵·배치 복사 및 내부 참조 재연결, 채팅·플레이 기록·테스트 확인 미복제, `DRAFT`로 시작
- [ ] [W6] 테스트 — 출시 검사 항목별 차단, 공개 후 모든 수정 경로 차단, 복제 후 참조가 새 사건을 가리키는지

## 13. 관리자 — 테스트 세션 (4.4절)

- [ ] [W6] `GET /api/admin/cases/{caseId}/test-rooms` — 테스트 세션 목록 (`countsForVerification` 포함)
- [ ] [W6] `POST /api/admin/cases/{caseId}/test-rooms` — `CONTENT_CONFIRMED`·맵 설정 필요, 미종료 테스트는 하나만, `is_test=true`
- [ ] [W6] 테스트 세션 플레이는 일반 세션 API 사용, 비공개 사건이라도 관리자 테스트 세션이면 허용
- [ ] [W6] `DELETE /api/admin/cases/{caseId}/test-rooms/{roomId}` — 미종료 테스트만, 처리 중 AI 요청 먼저 무효화 후 세션·종속 데이터 삭제, 결과 미생성, 사용량 로그 보존(FK null)
- [ ] [W6] `POST /api/admin/cases/{caseId}/test-verification` — 미종료 테스트 없음 + `content_updated_at` 이후 생성·종료된 테스트 1개 이상(`409 TEST_NOT_COMPLETED`)
- [ ] [W6] 미종료 테스트 존재 시 수정 재개·맵 변경·좌표 변경·출시 차단, 사건 참조 AI 작업 처리 중이면 편집 보류
- [ ] [W6] 테스트 — 테스트 결과 랭킹·일반 세션 목록 제외, 중단 후 늦은 응답 미반영

## 14. 관리자 — 비용 모니터링 (4.3절, `docs/api/admin-llm-usage.md`)

- [ ] [W6] `GET /api/admin/llm-usage` — 기간(`from`·`to`, 최대 366일)·`granularity`, 유형별·기간별 합계, 미확인 사용량 별도 집계, `currentMonth` 예산 현황(Asia/Seoul 달력월)
- [ ] [W6] `GET /api/admin/llm-usage/budget`, `PATCH /api/admin/llm-usage/budget` — USD 고정, 한도 양수(소수 2자리)·경고 임계값 1~100
- [ ] [W6] `alertLevel` 계산 — `NONE`/`WARNING`/`EXCEEDED`/`NOT_CONFIGURED`, 초과해도 AI 호출 차단 안 함
- [ ] [W6] 모델별 토큰 단가 설정값 (예상 비용 계산용)
- [ ] [W6] 테스트 — 월 경계 계산, 미확인 사용량을 0으로 합산하지 않음

## 15. 프론트엔드 — 공통 (3.9절, 5.2절, frontend.md)

- [x] [W1] 로그인 화면 — Google Identity Services 버튼, ID 토큰 → `POST /api/auth/google` (담당: FE-2, 메모: 실제 Google 로그인 확인 완료(INT))
- [x] [W1] 닉네임 설정 화면 — 검증 메시지·`409 NICKNAME_EXIST` 표시 (담당: FE-2)
- [x] [W1] 인증 상태 — 액세스 토큰은 Zustand 메모리, 리프레시 토큰은 `localStorage`, 앱 시작 시 한 곳에서 `refresh → users/me` 복원 (담당: FE-2)
- [x] [W1] 여러 탭 토큰 조정 — Web Locks(`shelved-auth`)로 저장·재발급·로그아웃 직렬화, 잠금 안에서 최신 리프레시 토큰 재확인, `INVALID_REFRESH_TOKEN` 시 저장값이 바뀌었으면 최신 값으로 1회 재시도, 네트워크·서버 오류에서는 토큰 유지, `storage` 이벤트로 다른 탭 로그아웃 반영 (`docs/api/auth-user.md`) (담당: FE-2)
- [x] [W1] 로그아웃 — 토큰 삭제, Query 캐시·사용자·세션 메모리 상태 정리 (담당: FE-2)
- [x] [W1] 라우트 보호 — 로그인 필요 화면, 관리자 화면(`role=ADMIN`), 닉네임 미설정 시 닉네임 화면으로 (담당: FE-2)
- [x] [W1] 공통 UI 컴포넌트 — Button, Modal, 입력창, 로딩·에러 표시 (`shared/components`) (담당: FE-2)
- [ ] [W2] 메인 화면 — 사건 카드 목록(비로그인 열람 가능), 진행 중 수사(이어하기·다시 채점하기), 지난 수사(결과 보기), "진행 중" 배지, 빈 목록·조회 실패 상태
- [ ] [W2] 수사 시작 — 비로그인이면 로그인 후 고른 사건으로 복귀, `409 ROOM_ALREADY_EXISTS`면 이어하기 안내 창
- [~] [W1~W6] 도메인별 MSW 목 핸들러 — 정상 응답과 `202`·`409`·`502` 등 명세 오류 재현 (담당: FE-2, 메모: auth 완료)

## 16. 프론트엔드 — 게임 화면 (3.5절, 3.9절, 5.2절, frontend.md)

### Phaser·맵

- [ ] [W2] `game/EventBus.ts`, `game/events.ts` — `INTERACT_REQUESTED`, `MAP_READY`, `SET_INPUT_ENABLED`, `PLACE_AT_SPAWN` 타입 정의
- [ ] [W2] `PhaserGame.tsx` — 마운트·언마운트 시 인스턴스 생성·`destroy`, 리스너 정리
- [ ] [W2] Boot/Preload/Investigation Scene — Tiled JSON·타일셋·스프라이트 로드 (`game/assets.ts`가 `manifest.json` 사용)
- [ ] [W2] 플레이어 이동(WASD·방향키), 충돌 레이어, 캐릭터는 배열로 관리(MVP 1명)
- [ ] [W2] NPC·물증 오브젝트 배치(`GET map`), 상호작용 반경 안에서 "E: 대화"/"E: 조사" 안내, E키로 `INTERACT_REQUESTED` (창 자동으로 열지 않음)
- [ ] [W2] `PLACE_AT_SPAWN` 처리, 로딩 완료 시 `MAP_READY`

### 화면 상태·조작

- [ ] [W2] 게임 UI 상태 한 곳에서 관리 — 열린 창 목록, Esc는 가장 최근 창 하나만 닫기, 텍스트 입력 중 WASD·E 무시
- [ ] [W2] 입력 허용 조건 계산 훅 — 맵 준비·유효한 연결·열린 창 없음·`IN_PROGRESS`·남은 시간 > 0 → `SET_INPUT_ENABLED` (단위 테스트 필수)
- [ ] [W2] 입장·재접속 흐름 — 최초: state·briefing·map → 맵 로딩 → `PLACE_AT_SPAWN` → `MAP_READY` → 수사 시작 / 재접속·새로고침: pause → state·notes·hints·accusations·map 복원 → 이어하기. 종료 상태면 결과 화면, `awaitingGrading`이면 채점 대기 화면(맵 로딩 안 함), `READY`면 수사 시작, `PAUSED`이고 시간이 남았으면 이어하기, start·resume이 409면 state 재조회 후 분기 (`docs/api/room.md`)
- [ ] [W2] 시작·이어하기 오버레이 — `READY` 수사 준비 화면(사전 정보), `PAUSED` 복원 화면(남은 시간·횟수·처리 중·실패 요청·미저장 노트 초안 안내)
- [ ] [W2] HUD — 사건명·난이도·상태 칩, 타이머(서버 값으로 매번 재보정, 0이면 입력 잠정 차단 후 서버 확인), 남은 힌트·지목(사용 완료와 처리 중 구분), 상시 표시
- [ ] [W2] `useHeartbeat` — `heartbeatIntervalMs`마다, 창·입력·AI 대기·백그라운드 탭과 무관하게 전송, 응답 `timer.status`에 따라 이어하기·결과 화면 이동
- [ ] [W2] `connectionId`는 메모리에만 보관, `409 CONNECTION_REPLACED` 시 조작·하트비트 중단 후 연결 교체 안내("이 화면에서 계속하기" = 재접속 흐름)
- [ ] [W2] 저장 후 나가기 — 노트 저장 완료 확인 → pause → `PAUSED` 확인 후 메인, 각 단계 실패 시 재시도 표시
- [ ] [W2] 오른쪽 패널 — 한 번에 하나(NPC 대화·대화 기록·물증 설명·힌트·사전 정보)
- [ ] [W2] 물증 설명 패널 — 조사 API 응답 표시, 자동 수집·노트 입력 없음
- [ ] [W2] 사전 정보 패널 — 진행 중 다시 읽어도 타이머 진행

### 개인 노트

- [ ] [W2] 노트 창 — 오른쪽 패널과 동시에 열기, 글자 수 표시, `editable=false`면 읽기 전용
- [ ] [W2] 자동 저장 — 입력 멈춤 후 저장, 한 번에 하나만 전송·끝난 뒤 최신 내용 재저장, 저장 중·완료·실패 표시, 간격·글자 수 상수는 `shared/constants` (단위 테스트 필수)
- [ ] [W2] 로컬 초안 — 사용자·세션별 키로 `localStorage` 보관, 저장 거부 시 "이 기기의 미저장 초안"으로 표시·복사 가능
- [ ] [W2] `409` 충돌 화면 — 서버 내용과 로컬 초안 비교, 서버 내용으로 바꾸기 / 내 초안으로 덮어쓰기(`currentRevision` 기준 재저장)
- [ ] [W2] 노트 창 닫을 때 미저장 내용 저장, 실패해도 입력 유지

### AI 요청 UI

- [ ] [W3] AI 요청 공통 훅 — 새 전송 시 `crypto.randomUUID()`, 재시도는 같은 `requestId`·입력, `202`면 2초 간격으로 `ai-requests` 조회(완료·실패·화면 이탈 시 해제), 처리 중 전송 비활성화 (단위 테스트 필수)
- [ ] [W3] NPC 대화창 — 말풍선(`sequenceNo` 순), 답변 생성 중 표시, 실패 시 "다시 보내기", `QUESTION_BLOCKED` 안내(기록 안 됨), 창을 닫아도 요청 취소 안 함
- [ ] [W3] 대화 기록 패널 — 대화한 NPC 목록(`conversations` + map 이름 조인), NPC별 원문 읽기 전용
- [ ] [W3] 힌트 대화창 — 기록 시간순, 전송 전 "전송하면 힌트 1회를 사용합니다(남은 N회)", 새 힌트·"이 힌트에 대해 다시 묻기(무료 1회)" 버튼 구분, 실패 시 "횟수는 차감되지 않았습니다"·재시도, 힌트 0회여도 미사용 보충 질문 가능
- [ ] [W3] 재접속 시 `aiRequests`·`pendingRequests`로 처리 중·실패 요청 복원
- [ ] [W3] 완료 결과 복구 후 현재 상태 재조회 — 재전송·폴링으로 받은 결과는 대화·답안 복원에만 쓰고, 남은 횟수·세션 상태·화면 전환은 `GET state`, 보충 질문 버튼은 `GET hints`로 갱신
- [ ] [W3] 보충 질문 전송 버튼 — 최신 `followupAvailable=true` + `IN_PROGRESS` + 남은 시간 > 0 + 현재 조작 연결일 때만 활성화

### 지목

- [ ] [W4] 범인 지목 창 — 대상은 `map.suspects`(배치 안 된 인물 포함, 스프라이트 없으면 기본 실루엣), 트릭 설명 10~3000자, 명확한 제출 버튼만(Enter 제출 금지), 채점 중 입력·제출 잠금
- [ ] [W4] 채점 결과·이전 지목 기록 표시(`verdict`, `feedback`, `attemptNo`), 응답 `roomStatus`가 종료면 결과 화면으로
- [ ] [W4] 채점 실패 — "같은 답안 다시 채점"(`retryAvailableAt` 전 비활성화), "나중에 다시 시도"로 메인 이동, 자동 반복 재시도 금지
- [ ] [W4] 시간 종료·채점 대기 화면 — `awaitingGrading=true`, 새 행동 금지·기록 조회 가능, 연결 헤더 없이 재채점
- [ ] [W4] 수사 기록 보기(읽기 전용) — NPC 원문·힌트 기록·서버 노트·로컬 초안, 새 요청 없음

## 17. 프론트엔드 — 결과/랭킹 (3.8절, 3.10절)

- [ ] [W4] 리빌 화면 — 결과 머리말(CLEARED/FAILED/EXPIRED), 원작 공개(`source`), 범인(스프라이트는 map 조인, `spriteKey`가 null이면 기본 실루엣), 트릭·해설, 플레이 기록, 내 지목 기록
- [ ] [W4] 결과 화면 랭킹 영역 — 내 최고 기록, 랭킹 목록, 다시 도전(`POST /api/rooms`)
- [ ] [W4] 관리자 테스트 결과 — 랭킹·다시 도전 숨김, "관리자 테스트 · 랭킹 제외", 맵·테스트 화면으로 복귀
- [ ] [W4] 랭킹 페이지 — 로그인 필요(비로그인이면 로그인 화면으로), 공동 순위 표시, 기록 없음 상태, 페이지 이동

## 18. 프론트엔드 — 관리자 화면 (4.2~4.6절, 와이어프레임 ⑤~⑧)

- [ ] [W5] 관리자 레이아웃 — 사건 목록·AI 사용량 메뉴, 예산 경고 배지(`currentMonth.alertLevel`)
- [ ] [W5] 사건 목록 — 상태 필터·개수, 새 사건, 상태별 이동(DRAFT → 제작, CONTENT_CONFIRMED → 맵·테스트, PUBLISHED → 읽기 전용·복제)
- [ ] [W5] 원문·표지 업로드 UI — 업로드 중 중복 방지, 실패 사유·재시도, 원문 없으면 채팅 비활성화
- [ ] [W5] 제작 채팅 — 말풍선, `toolChanges` 칩, `inputInfo` 표시, 처리 중 잠금(채팅·확정·원문 교체), `202`면 messages 폴링, 실패 시 같은 메시지로 재시도(`409 CASE_CHAT_CONTEXT_CHANGED`면 최신 상세·메시지를 다시 받고 새 `requestId`로 보내도록 안내), 복구·폴링 후에는 상세를 다시 조회해 메모장 갱신(과거 초안으로 덮어쓰지 않음), ⌘+Enter 전송
- [ ] [W5] 메모장 패널(읽기 전용) — 카드·원작 / 등장인물(persona ↔ 원작 근거) / 물증 / 트릭·판정 기준·뒷받침 단서 / 사전 정보·리빌 / 용어집 탭, 매 응답 `draft`로 다시 그림, "방금 변경" 강조
- [ ] [W5] 확정 전 검사 목록(`draftIssues`) 상시 표시, 콘텐츠 확정 버튼(`DRAFT_INVALID` 결과 표시)
- [ ] [W6] 맵·테스트·출시 화면 — 맵 설정 폼(에셋 목록 선택), 저장 오류 표시, Phaser 맵 미리보기(시작점·배치·반경), 배치 표(미배치·맵 밖 표시)
- [ ] [W6] 수정 재개 확인 창 — "테스트 기록 N개 삭제" 안내
- [ ] [W6] 테스트 세션 실행·이어하기·중단 UI, 테스트 완료 확인 버튼
- [ ] [W6] 출시 검사 목록·출시 버튼, 공개 사건 잠금 화면·초안으로 복제 버튼
- [ ] [W6] AI 사용량 대시보드 — 경고 배너, 이번 달 현황, 기간·단위 선택, 기간별 그래프, 유형별 표, 예산 설정 폼

## 19. 에셋 & 사건 콘텐츠 (8.2~8.3절, 4.6절)

### 에셋

- [ ] [W1~W2] LPC 계열 타일셋·캐릭터 소싱 & 라이선스 확인 (코딩 에이전트 담당) — 출처 URL·제작자·라이선스·버전·수정 여부·고지 조건, 합성 캐릭터는 부품별로
- [ ] [W2] `frontend/public/assets/CREDITS.md` 작성, 조건을 확인할 수 없는 에셋은 제외
- [ ] [W2] 정적 에셋 폴더 구조 확정 (`maps/`, `tilesets/`, `sprites/`, kebab-case 파일명) 및 `manifest.json`(spriteKey·맵 경로), 백엔드 `shelved.assets.base-path` 연결
- [ ] [W2] 플레이어 공통 스프라이트 1종, 사건별 NPC 스프라이트(3~5종씩)
- [ ] [W2] 공개 에셋 점검 — 맵 JSON·manifest에 범인 여부·정답·NPC 설정·물증 설명·원작 제목 미포함

### 사건 콘텐츠 (정답 정보는 로컬에만)

- [ ] [W1~W2] 사건 A 개발용 시드 — 인물·persona·물증·트릭 판정 기준·사전 정보·리빌 해설·용어집 (소유자 검수, 로컬 전용)
- [ ] [W2] 사건 A 맵 제작 (Tiled, 충돌 레이어 포함) 및 좌표 배치
- [ ] [W5] 사건 카드 표시명·키워드 태그 3편 확정 (소유자, `project-spec.md` 9절)
- [ ] [W1~W4] 사건 B·C 원문 `.txt` 미리 준비 — 5주차 실측 전까지 (소유자)
- [ ] [W5~W6] 사건 B·C 관리자 채팅으로 초안 작성 → 원작 일치·자백 없이 해결 가능한지 검수 → 확정 (소유자)
- [ ] [W6] 사건 B·C 맵 제작 및 좌표 배치
- [ ] [W6] 사건 3편 표지 이미지 준비·업로드 (원작 표지 금지) (소유자)
- [ ] [W6] 사건 A를 관리자 흐름(맵 배치 → 테스트 → 출시)으로 정식 등록
- [ ] [W6] 사건 3편 관리자 테스트·테스트 완료 확인·출시

## 20. 배포 / 운영 (10.1절)

- [ ] [W7] 시연 환경 결정 (로컬 시연 또는 배포 대상 선정) (소유자)
- [ ] [W7] 배포하는 경우 DB·업로드 파일(표지) 보존·복원 방법 README에 기록
- [ ] [W7] 시연용 DB 백업, `validate` 프로필 확인
- [ ] [W7] README 최종 점검 — README만 보고 로컬에서 실행·시연 가능한지

## 21. 검증 (11절)

### 자동 테스트 (각 기능 PR에 포함)

- [ ] [W2~W6] 권한 — 다른 사용자의 세션·노트·대화·AI 요청 결과 접근 차단, 비공개 사건 일반 입장 차단
- [ ] [W2~W4] 상태 전이·시간 경계·횟수 제한 (4·9·10절 테스트 항목)
- [ ] [W3~W4] 중복·동시 요청 — Testcontainers 통합 테스트 (`{대상}IntegrationTest`)
- [ ] [W4] 정답 비공개 — 종료 전 API 응답·맵·에셋에 정답 필드 미포함 확인
- [ ] [W4] 기록·랭킹 집계
- [ ] [W6] 관리자 상태 전이·출시 검사·공개 후 수정 차단
- [~] [W2~W6] 프론트엔드 필수 테스트 — `utils` 함수, 입력 허용 조건, 노트 자동 저장(한 번에 하나·409 시 초안 보존), `requestId` 재사용, 재접속 상태 분기(READY 복귀·종료·채점 대기), 여러 탭의 재발급·로그아웃 (담당: FE-2, 메모: 여러 탭 재발급·로그아웃 완료)

### 수동 플레이테스트 (로컬 `playtest/{YYYY-MM-DD}-{사건 식별명}.md`)

- [ ] [W3] 사건 A NPC 응답 1차 점검 — 설정된 거짓말·침묵, 정답 요구 차단과 정상 질문 오차단
- [ ] [W4] 사건 A 1차 플레이테스트 — 정답·부분 정답·오답·같은 의미의 다른 표현 채점, 난이도별 피드백, 힌트 정답 누설 여부, 보충 답변의 새 정보 추가 여부
- [ ] [W4] 사건 A 제한시간 실측 → 난이도별 제한시간 설정값 비례 조정 (2.3절)
- [ ] [W4] 실제 AI 응답 대기 시간 측정(평균·느린 요청·한 판 누적) → 시간 정책 재검토 (3.5절)
- [ ] [W7] 사건 3편 각각 수동 플레이테스트 — 원작 적합성, 자백 없이 해결 가능, 맵 접근성, 패널 조작, 노트 저장, 재접속
- [ ] [W7] 시연 전 최종 점검 — 3편 각각 정상 성공 흐름과 실패 종료 흐름
- [ ] [W7] 시연 리허설, `dev` → `main` 병합 (병합은 소유자)

---

## 결정 사항 (2026-10-30)

- [x] 시연일 — 잠정 2026-12-14(월)
- [x] 닉네임 대소문자 무시 UNIQUE — 정규화 컬럼 `nickname_lower` + UNIQUE (1절)
- [x] 직접 정답 요구 차단 — 질문마다 작은 모델로 의도 분류 호출, `QUESTION_FILTER`로 사용량 기록 (5절)
- [x] 구현 세부값 — 잠정 초기값으로 시작, 사건 A 플레이테스트(4주차) 후 조정 (0절)
- [x] 개발용 시드의 카드 표시명·태그 — 임시 이름 사용
- [x] 사건 B·C 원문 — 소유자가 5주차 전에 미리 준비

## 아직 정해야 할 것 (확정 전까지 관련 개발을 시작하지 않음)

`project-spec.md` 9절 "남은 항목"입니다. 결정 시한은 해당 기능을 시작하는 주차입니다.

- [ ] [W5 전] 사건 카드 표시명 / 키워드 태그 확정 (출시 검사 필수 항목)
- [ ] [W5] 실제 원문 3편 실측 기반 업로드 한도·입출력 예산·분할 크기·사용 모델 확정
- [ ] [W4 이후] 사건 A 플레이테스트 결과로 구현 세부값 조정
- [ ] [W7] 시연 환경 결정 (로컬 시연 또는 배포)
