# Shelved

> *Every case on the shelf is a story you already know — or do you?*

**Shelved**는 소설을 바탕으로 AI가 구성한 사건을 플레이어가 직접 수사하는 추리 웹 게임입니다.
플레이어는 2D 맵을 돌아다니며 용의자를 심문하고 물증을 조사한 뒤, 범인과 트릭을 밝혀냅니다.
사건의 바탕이 된 소설이 무엇인지는 마지막 순간에야 공개됩니다.

> 🚧 현재 **기획 단계**입니다. 개발이 진행되면 설치·실행 방법과 세부 문서가 추가됩니다.

<br>

## 컨셉

### 표지 없는 사건
메인 화면의 사건 카드에는 원작과 무관한 독자적인 사건명과 표지 이미지만 표시됩니다.
플레이어는 어떤 소설이 바탕인지 모른 채 수사를 시작하고, 게임이 끝나면 성공 여부와 관계없이 원작 소설이 공개됩니다.
플레이 도중 원작을 눈치채는 것도 재미의 일부입니다.

### AI와 함께 만들고, AI와 함께 푸는 사건
- **NPC 심문**: 각 용의자는 원작 속 인물이 알 수 있는 사실만을 바탕으로 자유 질문에 답합니다. 인물에 따라 무언가를 숨기거나, 거짓말을 하거나, 끝까지 침묵하기도 합니다.
- **의미 기반 채점**: 플레이어가 제출한 범인과 트릭 설명을 AI가 사건별 핵심 요소와 대조해 정답·부분 정답·오답으로 판정하고, 무엇이 맞았고 무엇이 빠졌는지 피드백합니다.
- **사건 제작 파이프라인**: 관리자가 저작권이 만료된 소설 원문을 업로드하면, AI와의 대화를 통해 등장인물·물증·트릭 등 사건 데이터를 만들고 검수합니다.

<br>

## 게임 플레이

1. 책장에서 사건 카드를 하나 고릅니다. 난이도는 사건마다 정해져 있습니다.
2. 맵 위에서 캐릭터를 움직여 용의자에게 말을 걸고, 오브젝트를 조사해 물증을 확인합니다.
3. 대화 기록을 다시 읽고, 개인 노트에 직접 추리를 정리합니다.
4. 막히면 질문형 힌트를 요청할 수 있습니다. 힌트 횟수는 난이도에 따라 다릅니다.
5. 확신이 서면 범인과 트릭을 함께 제출합니다. 기회는 최대 3번입니다.
6. 사건이 끝나면 바탕이 된 원작 소설이 공개되고, 기록이 랭킹에 남습니다.

| 난이도 | 힌트 | 수사 스타일 |
|---|---|---|
| 쉬움 | 5회 | NPC가 도움이 될 만한 이야기를 먼저 꺼내주기도 합니다 |
| 보통 | 3회 | NPC는 물어본 것에 친절하게 답합니다 |
| 어려움 | 1회 | 스스로 질문을 설계해 정보를 끌어내야 합니다 |

<br>

## 기술 스택 (예정)

| 영역 | 기술 |
|---|---|
| Frontend | React, Phaser.js |
| Backend | Spring Boot |
| Database | PostgreSQL |
| AI | Claude API |
| Auth | Google OAuth |
| Map / Asset | Tiled Map Editor, LPC(Liberated Pixel Cup) 에셋 |

<br>

## 로드맵

- [ ] **MVP — 솔로 플레이**: 사건 3편, NPC 심문·물증 조사·힌트·채점·리빌, 관리자 사건 제작 도구
- [ ] **멀티플레이 확장**: 여러 명이 한 사건을 실시간으로 함께 수사하는 협동 모드

<br>

## About

'게임과 디지털 네러티브' 수업의 학기 프로젝트로 진행하며, 2026년 12월 MVP 시연을 목표로 합니다.
사건의 바탕이 되는 소설은 모두 저작권이 만료된 퍼블릭 도메인 작품입니다.

<br>

## 로컬 실행

### 백엔드

#### 필요한 도구와 버전

| 항목 | 버전 |
|---|---|
| Java | 21 |
| Spring Boot | 4.1.0 (Gradle Wrapper 사용, Gradle 별도 설치 불필요) |
| PostgreSQL | 17 (Docker 이미지 `postgres:17-alpine`) |
| Docker | Docker Compose v2 포함 (로컬 DB 실행, Testcontainers 통합 테스트) |

#### 환경 변수

`backend/.env.example`을 `backend/.env`로 복사한 뒤 값을 채웁니다. `.env`는 Git에서 제외됩니다.

| 이름 | 용도 |
|---|---|
| `SPRING_PROFILES_ACTIVE` | 실행 프로필. `local`(폐기 가능한 로컬 DB, 스키마 자동 갱신, Swagger UI 노출) / `demo`(보존할 개발·시연 DB, 스키마 검증만) |
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD` | PostgreSQL 접속 정보. `docker-compose.yml`도 같은 값으로 DB를 만든다 |
| `GOOGLE_CLIENT_ID` | Google 로그인 ID 토큰의 대상(client ID) 검증 |
| `JWT_SECRET_KEY` | 액세스 토큰 서명 키 (UTF-8 32바이트 이상). 없거나 짧으면 서버가 기동하지 않는다 |
| `SHELVED_CORS_ALLOWED_ORIGINS` | (선택) 브라우저에서 API를 호출할 수 있는 출처. 기본값 `http://localhost:5173` |
| `CLAUDE_API_KEY` | Claude API 키 |
| `CLAUDE_MODEL` | 본 응답용 Claude 모델 (NPC 대화·힌트·채점·사건 제작) |
| `CLAUDE_FILTER_MODEL` | 질문 분류용 작은 Claude 모델 (직접적인 정답 요구 차단) |

Claude 관련 변수는 비어 있어도 서버가 기동됩니다. AI 기능을 쓸 때 필요합니다.
`GOOGLE_CLIENT_ID`가 비어 있어도 서버는 기동되지만 Google 로그인 요청은 모두 `GOOGLE_TOKEN_INVALID`로 실패합니다.

`JWT_SECRET_KEY`는 아래 명령으로 만든 임의 값을 `.env`에 넣습니다. 이 값이 바뀌면 이미 발급된 액세스 토큰은 모두 `INVALID_TOKEN`이 됩니다.

```bash
openssl rand -base64 32
```

CORS 허용 출처는 `application.yml`의 `shelved.cors.allowed-origins`(기본값 `http://localhost:5173`)입니다. 프론트엔드를 다른 주소에서 띄우면 `.env`에 쉼표로 구분해 지정합니다. 쿠키를 쓰지 않으므로 credentials는 허용하지 않습니다.

```bash
SHELVED_CORS_ALLOWED_ORIGINS=http://localhost:5173,http://127.0.0.1:5173
```

#### `.env` 주입 방법

애플리케이션은 `.env`를 자동으로 읽지 않습니다. 서버를 실행하는 **같은 셸**에서 아래처럼 환경 변수로 내보낸 뒤 실행합니다.

```bash
set -a; source .env; set +a
```

`docker compose`는 같은 폴더의 `.env`를 변수 치환에 자동으로 사용하므로 별도 주입이 필요 없습니다. IDE에서 실행할 때는 실행 구성의 환경 변수에 `.env` 내용을 직접 지정합니다.

#### 실행 명령 (작업 디렉터리: `backend/`)

```bash
cd backend
cp .env.example .env          # 처음 한 번, 값 수정

# DB 실행
docker compose up -d

# 서버 실행 (http://localhost:8080)
set -a; source .env; set +a
./gradlew bootRun

# 테스트 (Docker 실행 중이어야 함: Testcontainers PostgreSQL 사용)
./gradlew test

# 빌드 (테스트 포함)
./gradlew build
```

- Swagger UI: `local` 프로필에서만 열립니다. http://localhost:8080/swagger-ui.html
- DB 종료: `docker compose down` (데이터 유지)

#### 관리자 계정 만들기

관리자 가입 절차는 없습니다. Google로 한 번 로그인해 사용자를 만든 뒤 DB에서 역할을 직접 바꿉니다. 역할은 요청마다 DB 값으로 확인하므로 다시 로그인하지 않아도 바로 반영됩니다.

1. 프론트엔드에서 관리자로 쓸 Google 계정으로 로그인합니다 (닉네임도 설정해 둡니다. 닉네임이 없으면 관리자 API도 `NICKNAME_REQUIRED`).
2. DB에 접속해 해당 사용자를 찾습니다. `id`는 로그인 응답이나 `GET /api/users/me`의 `userId`로도 확인할 수 있습니다.

   ```bash
   cd backend
   docker compose exec postgres psql -U "$DB_USERNAME" -d "$DB_NAME"   # .env를 셸에 주입한 상태에서
   ```

   ```sql
   SELECT id, nickname, role FROM users ORDER BY id;
   ```

3. 역할을 바꾸고 확인합니다.

   ```sql
   UPDATE users SET role = 'ADMIN' WHERE id = 7;   -- 위에서 확인한 id
   SELECT id, nickname, role FROM users WHERE id = 7;
   ```

4. 확인: `GET /api/users/me` 응답의 `role`이 `ADMIN`이면 됩니다. 되돌릴 때는 `role = 'PLAYER'`로 바꿉니다.

#### DB 초기화

- **local**: 스키마는 JPA가 자동으로 만들고 갱신합니다(`ddl-auto=update`). 데이터를 모두 지우고 처음부터 시작하려면 볼륨까지 삭제한 뒤 다시 실행합니다.

  ```bash
  cd backend
  docker compose down -v
  docker compose up -d
  ```

- **demo**: 보존할 DB이므로 스키마를 자동으로 바꾸지 않습니다(`ddl-auto=validate`). 스키마 생성·변경은 백업 후 명시적인 SQL로 수행하며, 절차는 시연 환경을 정할 때 이 절에 추가합니다.
- **test**: 테스트마다 Testcontainers가 임시 DB를 띄우고 `create-drop`으로 스키마를 만듭니다. 로컬 DB와 분리되어 있습니다.

### 프론트엔드

- Node.js 26 (Active LTS). 버전은 `frontend/.nvmrc`에 고정되어 있으며, nvm을 쓰면 `frontend/`에서 `nvm use`로 맞출 수 있습니다.
- 패키지 매니저는 npm이며, 설치는 잠금 파일(`package-lock.json`) 기준의 `npm ci`로 합니다.

```bash
cd frontend
npm ci                  # 의존성 설치 (package-lock.json 기준)
npm run dev             # 개발 서버 실행 (http://localhost:5173)

npm run lint            # ESLint 검사
npm run typecheck       # TypeScript 타입 검사
npm run test -- --run   # 테스트 1회 실행 후 종료 (--run 없이 실행하면 파일 변경을 감시)
npm run build           # 배포용 빌드 (frontend/dist)
npm run format          # Prettier로 코드 정렬
```

#### 환경 변수

`frontend/.env.example`을 `frontend/.env.local`로 복사한 뒤 값을 채웁니다. Vite가 `npm run dev`·`npm run build` 실행 시 이 파일을 자동으로 읽습니다. `.env.local`은 Git에서 제외됩니다.

| 이름 | 용도 |
|---|---|
| `VITE_API_BASE_URL` | 백엔드 API 기본 주소 |
| `VITE_GOOGLE_CLIENT_ID` | Google 로그인 클라이언트 ID |
| `VITE_USE_MOCK` | `true`면 개발 서버에서 MSW 목 응답 사용 |

`VITE_`로 시작하는 변수는 빌드 결과에 그대로 포함되어 누구나 볼 수 있으므로, 공개해도 되는 값만 넣습니다.

#### 목(MSW) 모드

백엔드 없이 사전 설계 명세(`docs/api/`)대로 가짜 응답을 받아 개발할 때 사용합니다.

1. `frontend/.env.local`에 `VITE_USE_MOCK=true`를 설정하거나, 실행할 때 `VITE_USE_MOCK=true npm run dev`로 지정합니다.
2. 브라우저 개발자 도구 콘솔에 `[MSW] Mocking enabled.`가 표시되면 목 모드입니다.

목은 개발 서버에서만 동작하며, `npm run build` 결과에는 목 코드와 워커 파일(`mockServiceWorker.js`)이 포함되지 않습니다. 목 핸들러가 없는 요청은 실제 서버(`VITE_API_BASE_URL`)로 그대로 전달됩니다.
