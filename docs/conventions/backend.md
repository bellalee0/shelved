# 백엔드 컨벤션

> `backend/` 하위의 Spring Boot 코드에 적용되는 규칙입니다.
> Git·커밋·PR 등 공통 규칙은 [common.md](common.md)를 따릅니다.

## 목차

1. [기술 기반](#기술-기반)
2. [패키지 구조](#패키지-구조)
3. [명명 규칙](#명명-규칙)
4. [엔티티 규칙](#엔티티-규칙)
5. [로직 작성 규칙](#로직-작성-규칙)
6. [AI(LLM) 연동 규칙](#aillm-연동-규칙)
7. [정답 정보 보호 규칙](#정답-정보-보호-규칙)
8. [테스트 코드 작성 규칙](#테스트-코드-작성-규칙)

---

## 기술 기반

| 항목 | 결정 |
|---|---|
| 언어 | Java 21 |
| 프레임워크 | Spring Boot 4.1.0 |
| 빌드 도구 | Gradle |
| DB | PostgreSQL |
| 엔티티 ID | Java `Long`, DB auto increment (`GenerationType.IDENTITY`) |
| DB 스키마 관리 | JPA 자동 생성(`ddl-auto`). 필요해지면 Flyway를 도입한다 |
| API 명세 | 사전 설계 명세(`docs/api/`) + springdoc-openapi (Swagger UI) |
| 테스트 DB | Testcontainers PostgreSQL (통합 테스트용, 로컬 Docker 필요) |

- 스키마를 JPA가 자동 생성하므로, 엔티티의 필드·연관관계·제약 조건을 바꾸는 것이 곧 DB 스키마 변경이다. 이런 PR에는 데이터 영향과 적용 방법을 기록하고, 기존 호환성을 깨는 경우에만 [Breaking Change](common.md#breaking-change-기준)로 표시한다.
- 기획서 ERD의 `string id`는 모두 `Long` ID로 구현한다. 단, 클라이언트가 생성해 보내는 요청 식별자(`request_id`)와 연결 식별자(`connection_id`)는 엔티티 ID가 아니므로 `String`으로 둔다.
- Java·Spring Boot 버전, 실행 명령, 필수 환경 변수, DB 초기화 방법이 바뀌면 같은 PR에서 README를 함께 수정한다.
- 실제 사건의 개발용 시드 데이터는 로컬 전용 경로(`src/main/resources/dev-data/`)에 두고 커밋하지 않는다. 저장소에 커밋하는 샘플·테스트 데이터는 가상의 사건만 사용한다. ([common.md](common.md#정답-정보-보호-공개-저장소) 참고)

### 환경별 스키마 관리

- 테스트 전용 DB는 `ddl-auto=create-drop`으로 초기화할 수 있다. 개발·시연 DB와 연결 정보를 분리한다.
- 폐기 가능한 로컬 샘플 DB는 `update`를 사용할 수 있다. 실제 제작 사건·플레이 기록을 보존하는 개발·시연 환경은 `validate`를 사용하고 `create`·`create-drop`을 금지한다.
- 보존할 DB의 초기 생성·변경은 백업 후 명시적인 SQL 또는 문서화한 초기화 절차로 수행한다. `update`가 컬럼 이름 변경·데이터 변환을 안전하게 대신한다고 가정하지 않는다.
- 스키마 변경 시 적용 SQL·데이터 영향·복원 방법을 PR에 기록한다. 반복 변경 관리가 필요해질 때 Flyway를 검토하며 MVP 초기부터 강제하지 않는다.

### API 명세와 Swagger

API의 기준은 구현 전에 작성하는 사전 설계 명세(`docs/api/`)다. 작성·변경 절차는 [common.md](common.md#api-명세)를 따른다.

- Controller·DTO는 사전 설계 명세의 경로, 요청·응답 필드명, 상태 코드, 예외 코드를 그대로 구현한다.
- 구현 중 명세와 다르게 만들어야 할 이유가 생기면 코드만 바꾸지 않고, 같은 PR에서 명세를 먼저 고치고 구현을 맞춘다. 기존 호환성을 깨는 경우에만 Breaking Change로 표시한다.
- Swagger UI는 구현된 API를 직접 호출해 보고, 사전 설계 명세와 일치하는지 확인하는 용도로 사용한다.
- Controller와 요청·응답 DTO에 springdoc 어노테이션(`@Tag`, `@Operation`, `@Schema`)을 달아 설명을 채운다. 설명·예시 값에 실제 사건 정보를 쓰지 않는다.
- 로컬에서만 Swagger UI를 열어 두고, 운영 배포 시 노출 여부는 배포 대상을 정할 때 결정한다.

### 설정값 관리

기획서에서 "잠정치"로 정한 값과 플레이테스트로 조정할 값은 코드에 직접 쓰지 않고 `application.yml`에 두고 `@ConfigurationProperties`로 읽는다.

- 대상 예시: 난이도별 제한시간·힌트 횟수, 하트비트 이탈 판정 시간(15초), AI 작업 시간 제한, 채점 재시도 최소 간격(10초), 노트 글자 수 제한, 원문 업로드 크기 한도, 사용할 Claude 모델명, 입·출력 토큰 예산
- 설정 클래스는 `common/config`에 두고, 키는 `shelved.{영역}.{항목}` 형태로 쓴다. (예: `shelved.game.heartbeat-timeout`)
- API 키 등 비밀 값은 환경 변수로만 주입하고 `application.yml`·코드·저장소에 커밋하지 않는다.

```yaml
shelved:
  game:
    heartbeat-timeout: 15s
    grading-retry-min-interval: 10s
  llm:
    model: ${CLAUDE_MODEL}
    api-key: ${CLAUDE_API_KEY}
```

---

## 패키지 구조

루트 패키지는 `com.shelved`로 한다.

### common

여러 도메인에서 공통으로 사용하는 요소를 모아둔다.

- `entity` : 전체 엔티티
- `jwt` : 인증/인가 설정 및 필터
- `exception` : 공통 예외, 예외 코드, 전역 예외 핸들러
- `dto` : 공통 응답 객체 및 프로젝트 전역에서 사용할 dto
- `utils` : 프로젝트 내에서 사용할 자체 메서드
- `config` : 설정 클래스
- `enums` : 프로젝트 전역에서 사용하는 enum 관리
- `constants` : 프로젝트 전역에서 사용하는 상수 관리

### infra

외부 서비스와의 연동을 담당한다. 비즈니스 로직을 두지 않고, 도메인 서비스가 호출하는 클라이언트만 둔다.

- `llm` : Claude API 호출 클라이언트, 요청·응답 모델, 사용량 기록 연동
- `storage` : 표지 이미지 등 파일 저장

### domain

큰 도메인 단위로 패키지를 분류하고, 각 도메인 하위는 역할별로 동일하게 분리한다.

- `controller`
- `service`
- `repository`
- `dto`

```plain text
com.shelved
├── common
│   ├── entity
│   ├── jwt
│   ├── exception
│   ├── utils
│   ├── dto
│   ├── enums
│   ├── constants
│   └── config
├── infra
│   ├── llm
│   └── storage
└── domain
    ├── auth
    │   ├── controller
    │   ├── service
    │   ├── repository
    │   └── dto
    ├── user
    ├── cases
    ├── room
    └── ...
```

새 도메인을 추가할 때는 위 하위 구조(controller/service/repository/dto)를 그대로 따른다.

주기적으로 실행하는 작업(하트비트 미수신 세션 점검, 오래된 `PROCESSING` 요청 복구 등)이 필요한 도메인에 한해 `scheduler` 패키지를 추가한다. 스케줄러는 Service를 호출만 하고 로직을 직접 갖지 않는다.

### 초기 도메인 목록

구현하면서 조정할 수 있으며, 도메인을 추가·통합·분리하면 PR의 설계/구조 변경에 기록한다.

| 도메인 | 담당 |
|---|---|
| `auth` | Google OAuth 로그인, JWT 발급·재발급·로그아웃 |
| `user` | 닉네임 설정 등 사용자 정보 |
| `cases` | 플레이어용 사건 카드 목록 |
| `room` | 본인 세션 목록, 플레이 세션 생성·시작·일시정지·재개·하트비트·복원·결과(리빌) |
| `npc` | NPC 질문과 대화 기록 |
| `evidence` | 물증 조사 |
| `note` | 개인 노트 |
| `hint` | 질문형 힌트와 무료 보충 질문 |
| `accusation` | 범인·트릭 지목과 채점 |
| `ranking` | 사건별 랭킹 |
| `casebuilder` | 관리자 사건 제작 (원문 업로드, 제작 채팅, 초안, 확정, 맵·좌표, 테스트, 출시, 복제) |
| `llmusage` | 관리자 LLM 사용량 조회, 예산 설정 |

### 패키지 명명

- 소문자 한 단어로 작성하고, 두 단어 이상이면 붙여 쓴다. (예: `casebuilder`, `llmusage`)
- Java 예약어는 패키지명으로 사용할 수 없다. 사건 도메인은 `case` 대신 `cases`를 사용한다.

---

## 명명 규칙

### DTO 명명 규칙

도메인 + 행위 + Request/Response

```
example: RoomCreateRequest / NpcAskResponse / HintFollowupRequest
```

### Collection 명명 규칙

변수명 + Collection 타입(List/Page)

```
example: suspectList / rankingPage
```

### 메서드명 규칙

- 컨트롤러, 서비스의 메서드명은 반드시 동일하게 맞춘다 (같은 API를 처리하는 Controller/Service 메서드명 일치)
- 동사 + 명사

```
example: createRoom / getRoomState / askNpc / getHintList
```

```java
// Controller
@GetMapping("/rooms/{roomId}/state")
public ResponseEntity<CommonResponse<RoomStateResponse>> getRoomState(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long roomId
) {
    RoomStateResponse response = roomService.getRoomState(authUser.getId(), roomId);

    return ResponseEntity.status(HttpStatus.OK)
            .body(CommonResponse.success(ROOM_STATE_GET_SUCCESS, response));
}

// Service - Controller와 동일한 메서드명 사용
@Transactional(readOnly = true)
public RoomStateResponse getRoomState(Long userId, Long roomId) {

    Room room = roomRepository.findRoomById(roomId);
    room.validateParticipant(userId);

    return RoomStateResponse.from(room);
}
```

### 정적 팩토리 메서드 네이밍

- Entity → DTO 등 객체를 생성하는 정적 팩토리 메서드는 `from`으로 통일한다. `of`, `create` 등과 혼용하지 않는다.
- 파라미터가 많거나 타입이 섞여 `from`으로 읽기 어려운 경우에는 정적 팩토리 메서드를 쓰지 않고 생성자 또는 빌더를 사용한다.

```java
public record SuspectResponse(
        Long id,
        String name
) {

    public static SuspectResponse from(Suspect suspect) {
        return new SuspectResponse(
                suspect.getId(),
                suspect.getName()
        );
    }
}
```

---

## 엔티티 규칙

- 엔티티는 `common/entity`에 둔다.
- ID는 `Long` + `@GeneratedValue(strategy = GenerationType.IDENTITY)`를 사용한다.
- 테이블명은 기획서 ERD를 따라 snake_case 복수형으로 `@Table(name = "...")`에 명시한다. (예: `rooms`, `npc_messages`)
- enum 필드는 `@Enumerated(EnumType.STRING)`으로 저장한다. 순서값(`ORDINAL`)은 enum 순서가 바뀌면 기존 데이터가 깨지므로 사용하지 않는다.
- 스키마를 JPA가 생성하므로, 기획서의 UNIQUE 제약은 엔티티에 반드시 선언한다. 서비스 코드의 중복 검사만으로 대신하지 않는다. (예: `(room_id, request_id)`, `(room_id, evidence_id)`, `(room_id, user_id)`, `(conversation_id, sequence_no)`, `HINT_FOLLOWUPS.hint_id`, `PLAY_RESULTS.room_id`, `CASE_MAPS.case_id`)
- 원문·대화·노트·설명·프롬프트처럼 길이를 예측할 수 없는 문자열은 `@Column(columnDefinition = "text")`로 선언한다. 기본값(`varchar(255)`)으로 두지 않는다.
- 기획서의 `jsonb` 필드는 `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`로 매핑하고, Java에서는 `Map`이 아닌 전용 record 타입으로 다룬다.
- 시간 필드는 `LocalDateTime`을 사용하고, 누적 진행 시간처럼 밀리초 값은 `long`(`bigint`)으로 저장한다.

```java
@Entity
@Table(
        name = "ai_requests",
        uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "request_id"})
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    private AiRequestStatus status;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private AiRequestInput inputJson;

    ...
}

@Entity
@Table(name = "rooms")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    private RoomStatus status;

    ...
}
```

---

## 로직 작성 규칙

### 1. 공통 응답 객체 사용

모든 API 응답은 공통 응답 객체를 통해 반환하며, 공통 응답 객체는 Controller에서 생성한다.

```java
// X
@PostMapping("/rooms")
public ResponseEntity<CommonResponse<RoomCreateResponse>> createRoom(@Valid @RequestBody RoomCreateRequest request) {

    CommonResponse<RoomCreateResponse> response = roomService.createRoom(request);

    return ResponseEntity.status(HttpStatus.CREATED).body(response);
}

// O
@PostMapping("/rooms")
public ResponseEntity<CommonResponse<RoomCreateResponse>> createRoom(@Valid @RequestBody RoomCreateRequest request) {

    RoomCreateResponse response = roomService.createRoom(request);

    return ResponseEntity.status(HttpStatus.CREATED)
            // 공통 응답 객체 생성하여 반환
            .body(CommonResponse.success(ROOM_CREATE_SUCCESS, response));
}
```

공통 응답 객체(`common/dto/CommonResponse`)의 형식은 다음과 같다.

```java
@Getter
@RequiredArgsConstructor
public class CommonResponse<T> {

    private final boolean success;
    private final String message;
    private final T data;
    private final LocalDateTime timestamp;

    // 성공 시 (사용 예시: CommonResponse.success(SuccessMessage 이넘, 반환 DTO))
    public static <T> CommonResponse<T> success(SuccessMessage successMessage, T data) { ... }

    // 성공했는데 응답 데이터는 없을 시 (사용 예시: CommonResponse.successNodata(SuccessMessage 이넘))
    public static CommonResponse<Void> successNodata(SuccessMessage successMessage) { ... }

    // 예외 처리 시 (전역 예외 핸들러 및 인증·인가 오류 처리기에서 사용)
    public static CommonResponse<ErrorData> exception(ErrorCode errorCode, Object detail) { ... }
}

// 실패 응답의 data (common/dto/ErrorData)
public record ErrorData(
        String code,     // ErrorCode enum 이름
        Object detail    // 일부 예외에서만 제공하는 추가 정보, 없으면 null
) {
}
```

- 실패 응답은 `data`에 `{ code, detail }`을 담는다. 프론트엔드는 `message`를 사용자에게 보여주고, 분기는 `data.code`로 한다. 형식은 [docs/api/README.md](../api/README.md#실패-응답)를 따른다.
- `detail`이 필요한 예외(노트 충돌, 채점 재시도 간격 등)는 `CustomException(ErrorCode, detail)` 생성자로 던진다. 코드별 `detail` 형식은 [docs/api/errors.md](../api/errors.md)에 정의한다.

### 2. 공통 예외 처리 객체 사용

예외 처리는 공통 예외 객체 + 전역 예외 처리 핸들러를 통해 응답한다. 예외 메시지와 상태 코드는 일관성을 유지한다.

- 비즈니스 예외는 `CustomException(ErrorCode)` 또는 `CustomException(ErrorCode, detail)`로 던진다. 도메인별 예외 클래스를 따로 만들지 않는다.
- 인증·인가 필터의 오류는 `AuthenticationEntryPoint`·`AccessDeniedHandler` 및 JWT 필터의 오류 처리 경로에서 동일한 `CommonResponse.exception(...)` 형식으로 반환한다. MVC의 전역 핸들러가 필터 예외까지 처리한다고 가정하지 않는다. 응답 작성은 공통 헬퍼를 재사용하고 토큰·스택 트레이스를 노출하지 않는다.
- `GlobalExceptionHandler`(`@RestControllerAdvice`)가 `ErrorCode`의 상태 코드와 메시지로 `CommonResponse.exception(...)` 응답을 만든다.
- `@Valid` 검증 실패, 요청 본문 파싱 오류, 지원하지 않는 메서드, 타입 불일치, 외부 API 실패, 기타 런타임 예외도 전역 핸들러에서 공통 응답 형식으로 변환한다.

### 3. 성공 메시지·예외 코드·검증 메시지 관리

| 종류 | 위치 | 형식 | 이름 규칙 |
|---|---|---|---|
| 성공 메시지 | `common/enums/SuccessMessage` | enum (`message`) | `{도메인}_{행위}_SUCCESS` |
| 예외 코드 | `common/exception/ErrorCode` | enum (`HttpStatus status`, `message`) | `{대상}_{상태}` |
| 검증 메시지 | `common/constants/ValidationMessage` | `public static final String` 상수 | `{필드}_{조건}` |

- 각 파일 안에서는 도메인별로 주석(`// 인증`, `// 세션` 등)을 달아 묶는다.
- 사용하는 쪽에서는 `import static`으로 불러와 상수 이름만 쓴다.
- 메시지는 한국어로 작성한다. `ErrorCode` 메시지는 사용자에게 그대로 노출되므로 문장형(`~입니다.`)으로 쓴다.

```java
@Getter
@RequiredArgsConstructor
public enum SuccessMessage {

    // 세션
    ROOM_CREATE_SUCCESS("세션 생성 성공"),
    ROOM_STATE_GET_SUCCESS("세션 상태 조회 성공"),

    // 힌트
    HINT_CREATE_SUCCESS("힌트 요청 성공"),

    ;

    private final String message;
}

@Getter
@RequiredArgsConstructor
public enum ErrorCode {

    // 세션
    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "세션이 존재하지 않습니다."),
    ROOM_NOT_IN_PROGRESS(HttpStatus.CONFLICT, "진행 중인 세션이 아닙니다."),

    // 힌트
    HINT_LIMIT_EXCEEDED(HttpStatus.CONFLICT, "남은 힌트 횟수가 없습니다."),

    ;

    private final HttpStatus status;
    private final String message;
}

public final class ValidationMessage {

    // --- NPC 관련 ---
    public static final String QUESTION_NOT_BLANK = "질문 내용은 필수입니다.";
}
```

```java
// 사용 예시
@NotBlank(message = ValidationMessage.QUESTION_NOT_BLANK)
private String question;
```

### 4. 주석

- 메서드/클래스 전체에 대한 설명: `/** */` 사용
- 기타 설명: `//` 사용 (단, 로직의 동작 순서 등은 작성 X)

```java
/**
 * 범인·트릭 지목 제출 및 채점
 */
@Transactional
public AccusationCreateResponse createAccusation(Long userId, Long roomId, AccusationCreateRequest request) {
    ...
}
```

### 5. 가독성을 위한 개행

- 서비스단 메서드 시그니처 아래 한 줄 개행
- 논리적으로 다른 블록은 개행으로 구분

```java
public NoteResponse getMyNote(Long userId, Long roomId) {

    Room room = roomRepository.findRoomById(roomId);
    room.validateParticipant(userId);

    PlayerNote note = playerNoteRepository.findPlayerNote(roomId, userId);

    return NoteResponse.from(note);
}
```

### 6. 중복 로직은 추출(extract)

비즈니스 로직에서 중복되는 코드는 메서드로 추출하고, 추출된 메서드는 클래스 맨 밑에 둔다.

### 7. Controller 파라미터 가독성 규칙

메서드 파라미터는 3개 이상일 경우 한 줄에 하나씩 개행한다.

```java
@PostMapping("/rooms/{roomId}/npc/{suspectId}/ask")
public ResponseEntity<CommonResponse<NpcAskResponse>> askNpc(
        @AuthenticationPrincipal AuthUser authUser,
        @PathVariable Long roomId,
        @PathVariable Long suspectId,
        @Valid @RequestBody NpcAskRequest request
) {
    NpcAskResponse response = npcService.askNpc(authUser.getId(), roomId, suspectId, request);

    return ResponseEntity.status(HttpStatus.OK)
            .body(CommonResponse.success(NPC_ASK_SUCCESS, response));
}
```

### 8. Pageable 처리

Pageable 객체는 컨트롤러에서 처리한다 (서비스에서 Pageable 객체를 직접 생성하지 않는다). `@PageableDefault`를 사용한다.

```java
@GetMapping("/rankings/{caseId}")
public ResponseEntity<CommonResponse<PageResponse<RankingResponse>>> getRankingPage(
        @PathVariable Long caseId,
        @PageableDefault Pageable pageable
) {
    Page<RankingResponse> rankingPage = rankingService.getRankingPage(caseId, pageable);
    PageResponse<RankingResponse> response = PageResponse.from(rankingPage);

    return ResponseEntity.status(HttpStatus.OK)
            .body(CommonResponse.success(RANKING_GET_SUCCESS, response));
}
```

페이지 응답은 `PageResponse`로 변환해 사전 명세의 `content`, `totalElements`, `totalPages`, `size`, `number`만 제공한다. Spring의 `Page`를 API에 직접 직렬화하지 않는다.

### 9. 중간 변수 사용

가독성을 위해 중간 변수를 사용해서 return 한다.

```java
// X
@PostMapping("/rooms")
public ResponseEntity<CommonResponse<RoomCreateResponse>> createRoom(@Valid @RequestBody RoomCreateRequest request) {

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.success(ROOM_CREATE_SUCCESS, roomService.createRoom(request)));
}

// O
@PostMapping("/rooms")
public ResponseEntity<CommonResponse<RoomCreateResponse>> createRoom(@Valid @RequestBody RoomCreateRequest request) {

    RoomCreateResponse response = roomService.createRoom(request);

    return ResponseEntity.status(HttpStatus.CREATED)
            .body(CommonResponse.success(ROOM_CREATE_SUCCESS, response));
}
```

### 10. 리포지토리 default 메서드 사용

Optional로 반환되어 반복적으로 예외 처리가 필요한 경우, 예외 처리는 리포지토리에서 진행한다. 각 리포지토리 하단에 default 메서드를 생성한다.

```java
// X
public class RoomService {

    @Transactional(readOnly = true)
    public RoomStateResponse getRoomState(Long userId, Long roomId) {

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new CustomException(ErrorCode.ROOM_NOT_FOUND));
        room.validateParticipant(userId);

        return RoomStateResponse.from(room);
    }
}

// O
public interface RoomRepository extends JpaRepository<Room, Long> {

    default Room findRoomById(Long roomId) {
        return findById(roomId)
            .orElseThrow(() -> new CustomException(ROOM_NOT_FOUND));
    }
}

public class RoomService {

    @Transactional(readOnly = true)
    public RoomStateResponse getRoomState(Long userId, Long roomId) {

        Room room = roomRepository.findRoomById(roomId);
        room.validateParticipant(userId);

        return RoomStateResponse.from(room);
    }
}
```

### 11. HTTP 상태 코드 명시

`ResponseEntity`는 항상 `ResponseEntity.status(HttpStatus.X).body(...)` 형태로 상태 코드를 명시한다. `ResponseEntity.ok(...)`, `ResponseEntity.created(uri)`, `ResponseEntity.noContent()` 등 상태 코드가 메서드명에 축약되어 있는 형태는 사용하지 않는다.

```java
// X
return ResponseEntity.ok(CommonResponse.success(USER_GET_SUCCESS, response));

// O
return ResponseEntity.status(HttpStatus.OK)
        .body(CommonResponse.success(USER_GET_SUCCESS, response));
```

상태 코드는 API의 의미에 맞게 선택한다.

- `HttpStatus.CREATED` (201) : 새로운 리소스를 생성하는 API (예: 세션 생성, 사건 초안 생성)
- `HttpStatus.ACCEPTED` (202) : 같은 AI 요청이 아직 처리 중이어서 결과 대신 처리 상태를 반환하는 경우
- `HttpStatus.OK` (200) : 그 외 대부분의 API (조회, 수정, 삭제, 로그인/로그아웃/토큰 재발급, NPC 질문·힌트·지목처럼 결과를 바로 반환하는 액션)
- 오류 상태 코드는 [docs/api/README.md](../api/README.md#http-상태-코드)의 목록(400, 401, 403, 404, 405, 409, 429, 500, 502)만 사용한다. 세부 원인은 상태 코드를 늘리지 않고 `ErrorCode`의 메시지와 코드로 구분한다.
- 오류 상태 코드는 Controller에서 직접 반환하지 않고 공통 예외로 처리한다.

### 12. 시간은 서버 기준으로 계산

- 제한시간·일시정지·지목 접수 시각 등 게임 시간은 서버 시각으로만 판단한다. 클라이언트가 보낸 시각은 판정에 사용하지 않는다.
- 게임 규칙 판정에 쓰는 현재 시각은 `LocalDateTime.now()`를 직접 호출하지 않고 주입받은 `Clock`으로 구한다. 테스트에서 시간을 고정하기 위함이다. (공통 응답 객체의 `timestamp`처럼 판정과 무관한 시각은 예외)

### 13. 외부 AI 호출 중에는 트랜잭션을 유지하지 않는다

Claude API 응답을 기다리는 동안 DB 트랜잭션·잠금을 잡고 있지 않는다. 요청 접수(임시 확보)와 결과 확정을 각각 짧은 트랜잭션으로 나누고, 그 사이에 AI를 호출한다.

```java
// X - AI 응답을 기다리는 동안 트랜잭션이 열려 있음
@Transactional
public HintResponse createHint(...) {
    ...
    String answer = llmClient.call(prompt);
    ...
}

// O - 접수 → AI 호출 → 결과 확정
public HintResponse createHint(...) {

    AiRequest aiRequest = aiRequestService.accept(...);      // 짧은 트랜잭션

    LlmResult result = llmClient.call(prompt);               // 트랜잭션 밖

    return hintResultService.complete(aiRequest, result);    // 짧은 트랜잭션
}
```

- 하트비트·상태 조회·일시정지 요청은 AI 응답을 기다리는 요청 때문에 막히지 않아야 한다. AI 호출 경로와 같은 잠금을 오래 공유하지 않는다.

### 14. 권한 검사는 서버의 Service에서 한다

- 세션(`room`) 하위의 모든 API는 요청자가 해당 세션의 참가자인지 Service에서 확인한다. `roomId`, `requestId`, `hintId` 등 식별자를 안다는 것만으로 접근을 허용하지 않는다.
- 경로의 하위 식별자가 상위 리소스에 속하는지도 확인한다. (예: `suspectId`가 해당 세션의 사건 NPC인지, `hintId`가 해당 세션의 힌트인지)
- 관리자 API는 `USERS.role=ADMIN`으로 검사한다. 테스트 세션·비공개 사건 조회도 관리자만 허용한다.
- 공개된 사건의 수정 차단처럼 상태에 따른 제한은 모든 수정 경로에서 서버가 검사한다. 프론트엔드에서 버튼을 숨기는 것만으로 처리하지 않는다.

### 15. 세션 상태 변경은 세션 단위로 직렬화한다

세션 종료, 일시정지·재개, 결과 저장, 기회 사용 확정처럼 세션 상태나 남은 횟수를 바꾸는 작업은 같은 세션 기준으로 한 번에 하나씩 처리한다.

- 짧은 트랜잭션 안에서 해당 `rooms` 행을 비관적 잠금(`@Lock(PESSIMISTIC_WRITE)`)으로 조회한 뒤 상태를 확인하고 변경한다.
- AI 결과를 반영하기 전에 세션이 이미 종료되지 않았는지, 해당 요청의 `execution_no`가 최신인지 확인한다. 조건에 맞지 않는 늦은 응답은 게임 데이터·횟수에 반영하지 않고 사용량 로그만 남긴다.
- 같은 요청이 두 번 와도 시간이 두 번 누적되거나 결과(`PLAY_RESULTS`)가 두 번 생성되지 않도록 작성한다.

### 16. 클라이언트가 보낸 버전으로 덮어쓰기를 막는다

개인 노트처럼 자동 저장되는 데이터는 요청의 `revision`이 DB의 현재 값과 같을 때만 갱신하고 버전을 1 올린다. 다르면 `ErrorCode`의 409 예외로 충돌을 알리며, 서버 내용을 임의로 덮어쓰지 않는다.

---

## AI(LLM) 연동 규칙

- Claude API는 `infra/llm`의 클라이언트를 통해서만 호출한다. 도메인 서비스에서 HTTP 요청을 직접 만들지 않는다.
- 모든 Claude API 호출은 성공·실패와 관계없이 `LLM_USAGE_LOGS`에 기록한다. 재시도도 호출마다 기록한다.
  - `request_type`, `call_status`, `error_code`를 함께 남긴다.
  - 게임 호출은 `room_id`·`case_id`, 관리자 제작 호출은 `case_id`를 기록한다.
  - 토큰 사용량을 확인할 수 없으면 `usage_known=false`로 두고 토큰·비용을 `null`로 저장한다. 0으로 저장하지 않는다.
- 프롬프트는 Java 코드 안에 긴 문자열로 작성하지 않고 `src/main/resources/prompts/`에 파일로 관리한다.
- 관리자 제작 채팅의 tool 정의(`add_suspect`/`update_suspect`/`remove_suspect` 등)는 `casebuilder` 도메인에 두고, tool 인자와 변경 후 초안의 구조·참조를 검증한 뒤 한 응답의 변경을 한 번에 저장한다.
- AI 응답을 구조화된 결과(채점 결과, 초안 갱신 등)로 사용할 때는 서버에서 형식을 검증한다. 형식이 잘못된 응답은 AI 호출 실패로 처리하며, 플레이어의 오답이나 기회 소모로 처리하지 않는다.
- 게임플레이 AI 요청(NPC 질문, 힌트, 보충 질문, 지목)과 관리자 제작 채팅은 `request_id`로 중복 요청을 구분한다. 게임플레이 요청은 정상 결과가 저장된 경우에만 기회 사용을 확정한다.
- 직접적인 정답 요구 차단은 AI 요청 접수·기회 확보 전에 수행하고, 차단된 질문은 대화·힌트 기록에 저장하지 않는다.
  - 판정은 설정값으로 지정한 작은 모델(`shelved.llm.filter-model`)에 의도 분류를 요청해 수행한다. 단순 금칙어 목록으로 대신하지 않는다.
  - 분류 호출도 `request_type=QUESTION_FILTER`로 사용량을 기록한다. 분류 호출이 실패하거나 형식이 잘못되면 질문을 접수하지 않고 해당 API의 `502` 실패로 처리한다(기회 미차감).
- AI 호출에는 설정값으로 정한 시간 제한을 두고, 서버 재시작 등으로 남은 `PROCESSING` 요청은 실패로 복구한다.
- 원문을 모델 입력 한도에 맞추지 못하면 조용히 잘라 보내지 않고 오류로 알린다.

---

## 정답 정보 보호 규칙

플레이어가 결과를 보기 전에 정답·원작 정보가 응답에 섞여 나가지 않도록 다음을 지킨다.

- API 응답에 Entity를 그대로 반환하지 않고 항상 Response DTO로 변환한다.
- 아래 필드는 플레이어용 Response DTO에 포함하지 않는다. 리빌 결과 조회 등 종료 후 API에서만 예외적으로 포함한다.
  - `CASES`: `source_novel_title`, `source_novel_author`, `source_novel_intro`, `culprit_suspect_id`, `trick_summary`, `reveal_explanation`
  - `SUSPECTS`: `is_culprit`, `persona_prompt`
  - `CASE_TRICK_ELEMENTS`: `grading_rubric`
  - `ACCUSATIONS`: `element_results` (플레이어에게는 `feedback`만 반환)
- 아래 데이터는 종료 후에도 플레이어에게 반환하지 않는다.
  - `CASES.glossary` 전체 (생성 결과의 표기에만 반영)
  - `AI_REQUESTS.input_json` 등 AI에 전달한 내부 입력 컨텍스트
  - `CASE_SOURCE_TEXTS`, `CASE_DRAFTS`, `ADMIN_CASE_MESSAGES` 등 관리자 제작 데이터
- 물증 설명(`EVIDENCES.description`)은 조사 API 응답으로만 반환하고, 맵·세션 조회 응답에는 포함하지 않는다.
- 맵 조회 API는 공개용 배치 식별자와 렌더링 정보(좌표, `sprite_key`, 대화 가능 여부)만 반환한다.
- 관리자용과 플레이어용 응답은 같은 DTO를 공유하지 않는다.

---

## 테스트 코드 작성 규칙

### Given-When-Then 구조 사용

테스트 메서드 내부를 given / when / then 세 블록으로 명확히 구분하고 주석으로 표시한다.

```java
@Test
void getRoomState_성공() {

    // given
    Long roomId = 1L;
    Room room = RoomFixture.create(roomId);
    given(roomRepository.findRoomById(roomId)).willReturn(room);

    // when
    RoomStateResponse response = roomService.getRoomState(1L, roomId);

    // then
    assertThat(response.id()).isEqualTo(roomId);
}
```

### 테스트 메서드명

`메서드명_상황_결과` 형태로 작성하며, 상황과 결과는 한국어로 쓸 수 있다. 상황이 명확하면 생략한다.

```
example: getRoomState_성공 / createHint_횟수소진_예외 / createAccusation_시간만료후제출_예외
```

### API 생성 시 테스트코드 즉시 작성

- 컨트롤러/서비스 API 구현 직후, 같은 PR 안에서 테스트 코드까지 함께 작성한다.
- 테스트는 Service 내 메서드를 기준으로 단위 테스트로 진행한다.
- Claude API는 테스트에서 실제로 호출하지 않고 `infra/llm` 클라이언트를 Mock으로 대체한다.

### 반드시 테스트할 항목

기획서 11절의 자동 검증 대상이다. 해당 기능을 구현하는 PR에서 함께 작성한다.

| 항목 | 예시 |
|---|---|
| 권한 | 다른 사용자의 세션·노트·대화·AI 요청 결과 접근 차단, 비공개 사건 입장 차단 |
| 상태 전이 | 허용되지 않은 상태에서의 시작·재개·조사·질문·지목 거부 |
| 시간 경계 | 제한시간 직전·직후 제출, 일시정지 구간 제외, 마감 전 접수 답안의 재채점 |
| 횟수 제한 | 힌트·보충 질문·지목 한도, 처리 중 임시 확보분 포함 계산 |
| 중복·동시 요청 | 같은 `request_id` 재전송 시 같은 결과 반환, 동시 요청 시 한도 초과·중복 저장 없음 |
| AI 오류 | 시간 초과·서비스 오류·형식 불일치 시 기회 미차감, 늦은 응답 미반영 |
| 기록·랭킹 | 종료마다 결과 1건 생성, 테스트 세션 제외, 사용자별 최고 기록, 공동 순위(1·2·2·4) |

- AI 지연·오류·형식 불일치는 `infra/llm` 클라이언트의 대체 구현(Fake)으로 재현한다.
- DB 제약·잠금에 의존하는 동시 요청 테스트는 단위 테스트로 증명할 수 없으므로, Testcontainers PostgreSQL을 사용하는 통합 테스트로 작성한다. 통합 테스트 클래스명은 `{대상}IntegrationTest`로 한다.
- 시간 경계 테스트는 고정된 `Clock`을 주입해 작성한다.
