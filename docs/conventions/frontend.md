# 프론트엔드 컨벤션

> `frontend/` 하위의 React + Phaser 코드에 적용되는 규칙입니다.
> Git·커밋·PR 등 공통 규칙은 [common.md](common.md)를 따릅니다.
> 저장소 소유자는 프론트엔드 경험이 없으므로, 에이전트는 프론트엔드 작업 시 무엇을 왜 그렇게 했는지 설명을 덧붙입니다.

## 목차

1. [기술 기반](#기술-기반)
2. [폴더 구조](#폴더-구조)
3. [명명 규칙](#명명-규칙)
4. [컴포넌트 작성 규칙](#컴포넌트-작성-규칙)
5. [API 통신 규칙](#api-통신-규칙)
6. [React와 Phaser의 역할 분리](#react와-phaser의-역할-분리)
7. [조작·창 관리 규칙](#조작창-관리-규칙)
8. [게임 상태·시간 처리 규칙](#게임-상태시간-처리-규칙)
9. [에셋 관리](#에셋-관리)
10. [테스트 코드 작성 규칙](#테스트-코드-작성-규칙)
11. [용어 설명](#용어-설명)

---

## 기술 기반

| 항목 | 결정 | 선택 이유 |
|---|---|---|
| 언어 | TypeScript | 백엔드 DTO와 같은 모양의 타입을 정의해, API 응답 필드 오타·누락을 실행 전에 잡는다 |
| UI 라이브러리 | React | 화면(카드 목록, 대화창, 노트, 랭킹 등) 구성 |
| 게임 엔진 | Phaser 3 | 맵 렌더링, 캐릭터 이동, 충돌, 상호작용 범위 판정 |
| 빌드 도구 | Vite | 개발 서버 실행과 빌드가 빠르고 설정이 단순하다. Phaser 공식 React 템플릿도 Vite를 사용한다 |
| 패키지 매니저 | npm | Node.js에 기본 포함되어 별도 설치가 필요 없다 |
| 라우팅 | React Router | URL별 화면 전환 (`/cases`, `/rooms/:roomId` 등) |
| 서버 데이터 관리 | TanStack Query | API 호출 결과의 로딩·에러·재시도·캐시를 일관되게 처리한다 |
| 클라이언트 상태 관리 | Zustand | 로그인 정보처럼 여러 화면이 공유하는 작은 상태만 관리한다 |
| HTTP 클라이언트 | axios | 토큰 자동 첨부, 토큰 만료 시 재발급 처리를 한 곳에서 한다 |
| 스타일 | Tailwind CSS | 별도 CSS 파일·클래스 이름 없이 컴포넌트 안에서 스타일을 작성해, 여러 에이전트가 작업해도 스타일 방식이 흩어지지 않는다 |
| 코드 검사·정렬 | ESLint + Prettier | 문법 오류 검사와 코드 모양(들여쓰기, 따옴표 등) 자동 정렬 |
| 테스트 | Vitest + React Testing Library | Vite와 설정을 공유하는 테스트 도구 |
| API 목(mock) | MSW (Mock Service Worker) | 백엔드가 완성되기 전에도 사전 설계 명세대로 가짜 응답을 돌려줘, 프론트엔드를 병렬로 개발할 수 있다 |

- Node.js는 LTS 버전을 사용하며, 프로젝트 초기 설정 시 `frontend/.nvmrc`에 버전을 고정한다.
- 새 라이브러리를 추가할 때는 위 표에 있는 도구로 해결할 수 없는지 먼저 확인하고, 추가 시 PR의 설계/구조 변경에 이유를 적는다.

---

## 폴더 구조

기능(도메인) 단위로 폴더를 나눈다. 도메인 이름은 백엔드 도메인과 맞춰서, 같은 기능의 백엔드·프론트엔드 코드를 쉽게 찾을 수 있게 한다.

```plain text
frontend
├── public
│   └── assets               # 맵(Tiled JSON), 타일셋, 캐릭터 스프라이트 등 게임 에셋
│       ├── manifest.json    # sprite_key·맵 경로 목록 (백엔드 출시 검사와 공유)
│       └── CREDITS.md       # 에셋 출처·라이선스 표기
└── src
    ├── app                  # 앱 시작점: 라우터, 전역 Provider, App.tsx
    ├── pages                # URL 하나에 대응하는 화면 (조립만 담당)
    │   ├── LoginPage.tsx
    │   ├── CaseListPage.tsx # 메인: 사건 카드 + 본인 세션 목록(이어하기·결과 보기)
    │   ├── GamePage.tsx
    │   ├── ResultPage.tsx
    │   ├── RankingPage.tsx
    │   └── admin
    ├── features             # 도메인별 기능 (백엔드 domain과 대응)
    │   ├── auth
    │   ├── cases
    │   ├── room
    │   ├── npc
    │   │   ├── api.ts       # 이 도메인의 API 호출 함수
    │   │   ├── types.ts     # 요청·응답 타입
    │   │   ├── hooks        # 이 도메인의 커스텀 훅 (useAskNpc 등)
    │   │   └── components   # 이 도메인의 컴포넌트 (NpcDialog 등)
    │   ├── evidence
    │   ├── note
    │   ├── hint
    │   ├── accusation
    │   ├── ranking
    │   ├── casebuilder
    │   └── llmusage
    ├── game                 # Phaser 코드 (React 코드를 두지 않음, PhaserGame.tsx 제외)
    │   ├── PhaserGame.tsx   # Phaser를 React 화면에 붙이는 연결 컴포넌트
    │   ├── EventBus.ts      # React ↔ Phaser 이벤트 통로
    │   ├── events.ts        # 이벤트 이름·데이터 타입 정의 (기획서 5.2절 연동 계약)
    │   ├── assets.ts        # 에셋 목록: sprite_key·맵 경로 → 파일 경로
    │   ├── scenes           # Phaser Scene (Boot, Preload, Investigation 등)
    │   └── objects          # 플레이어, NPC, 물증 오브젝트
    ├── mocks                # MSW 목 핸들러 (개발용, 도메인별 파일)
    └── shared               # 여러 도메인이 함께 쓰는 코드
        ├── api              # axios 인스턴스, 공통 응답 타입, 에러 처리
        ├── components       # Button, Modal 등 공통 UI
        ├── hooks
        ├── utils
        └── constants
```

- `pages`는 `features`의 컴포넌트 조립과 간단한 UI 연결을 담당하고, API 호출·비즈니스 로직을 직접 작성하지 않는다.
- 한 도메인에서만 쓰는 코드는 해당 `features/{도메인}`에 둔다. 둘 이상의 도메인에서 재사용하는 범용 코드만 `shared`로 옮긴다. 화면 조립만을 위해 도메인 전용 코드를 이동하지 않는다.
- `features` 간에는 서로의 내부 파일을 직접 가져오지 않는 것을 원칙으로 한다. 여러 기능의 조립은 페이지나 상위 컴포넌트의 Props·콜백으로 연결하고, 범용 코드가 실제로 필요한 경우에만 `shared`로 추출한다.

---

## 명명 규칙

| 대상 | 규칙 | 예시 |
|---|---|---|
| 컴포넌트, 컴포넌트 파일 | PascalCase, 파일명 = 컴포넌트명 | `NpcDialog.tsx` → `NpcDialog` |
| 페이지 컴포넌트 | PascalCase + `Page` | `CaseListPage` |
| 커스텀 훅 | `use` + PascalCase | `useAskNpc`, `useHeartbeat` |
| 일반 함수, 변수 | camelCase, 동사 + 명사 | `askNpc`, `formatRemainingTime` |
| 컴포넌트가 아닌 파일 | camelCase | `api.ts`, `formatTime.ts` |
| 타입 | PascalCase, 접두사(`I`, `T`) 없음 | `RoomState`, `SuspectSummary` |
| Props 타입 | 컴포넌트명 + `Props` | `NpcDialogProps` |
| 상수 | UPPER_SNAKE_CASE | `HEARTBEAT_INTERVAL_MS` |
| 이벤트 핸들러 | Props는 `on` + 동작, 내부 함수는 `handle` + 동작 | `onSubmit` / `handleSubmit` |
| Phaser 이벤트 이름 | UPPER_SNAKE_CASE, `events.ts`에 상수로 정의 (기획서 5.2절과 동일) | `INTERACT_REQUESTED` |

### API 타입 이름

백엔드 DTO 이름과 **똑같이** 짓는다. 같은 API의 요청·응답을 백엔드와 프론트엔드에서 같은 이름으로 검색할 수 있게 하기 위함이다.

```ts
// features/npc/types.ts
// 백엔드 NpcAskRequest / NpcAskResponse와 필드를 맞춘다 (docs/api/investigation.md)
export type NpcAskRequest = {
  requestId: string;
  question: string;
};

export type NpcMessageResponse = {
  messageId: number;
  senderType: 'USER' | 'NPC';
  content: string;
  sequenceNo: number;
  createdAt: string;
};

export type NpcAskResponse = {
  requestId: string;
  suspectId: number;
  question: NpcMessageResponse;
  answer: NpcMessageResponse;
};
```

- 백엔드의 `Long` ID는 `number`로 받는다.
- 타입은 사전 설계 명세(`docs/api/`)를 기준으로 작성한다. 백엔드 구현이 끝나기를 기다리지 않는다.
- 백엔드 DTO가 바뀌면 같은 PR에서 프론트엔드 타입도 함께 수정한다.

---

## 컴포넌트 작성 규칙

### 1. 함수 컴포넌트와 named export 사용

- 컴포넌트는 함수로 작성한다. 클래스 컴포넌트는 사용하지 않는다.
- `export default` 대신 이름을 붙여 내보낸다(named export). 파일 이름과 가져다 쓰는 이름이 항상 같아져 검색·수정이 쉽다.
- 재사용하거나 복잡한 컴포넌트는 별도 파일로 둔다. 한 화면에서만 쓰는 짧은 내부 컴포넌트는 같은 파일에 둘 수 있다.

```tsx
// X
export default function ({ suspectName }) { ... }

// O
type NpcDialogProps = {
  suspectName: string;
  onClose: () => void;
};

export function NpcDialog({ suspectName, onClose }: NpcDialogProps) {
  return (
    <div>
      <h2>{suspectName}</h2>
      <button onClick={onClose}>닫기</button>
    </div>
  );
}
```

### 2. 화면 표시와 로직 분리

- 컴포넌트는 화면을 그리는 일에 집중한다.
- API 호출, 재사용 로직, 복잡한 상태 처리는 커스텀 훅(`hooks/`)으로 분리한다. 간단한 표시 계산과 해당 컴포넌트에서만 쓰는 UI 상태·핸들러는 컴포넌트 안에 둘 수 있다. 사소한 계산마다 훅이나 파일을 만들지 않는다.

```tsx
// X - 컴포넌트 안에서 API를 직접 호출
export function HintPanel({ roomId }: HintPanelProps) {
  const [hintList, setHintList] = useState<Hint[]>([]);

  useEffect(() => {
    axios.get(`/api/rooms/${roomId}/hints`).then((res) => setHintList(res.data.data.hints));
  }, [roomId]);
  ...
}

// O - 훅이 데이터 처리를 맡고, 컴포넌트는 결과만 사용
export function HintPanel({ roomId }: HintPanelProps) {
  const { data, isLoading } = useHintList(roomId);
  ...
}
```

### 3. 타입 명시

- `any`는 사용하지 않는다. 타입을 알 수 없는 외부 데이터는 `unknown`으로 받고 확인 후 사용한다.
- 컴포넌트 Props와 API 요청·응답에는 반드시 타입을 정의한다.

### 4. 주석

- 컴포넌트·훅 전체 설명: `/** */` 사용
- 기타 설명: `//` 사용 (단, 코드 동작 순서를 그대로 풀어 쓰지 않는다)

### 5. 스타일

- Tailwind CSS 클래스로 작성한다. 별도 `.css` 파일은 전역 스타일(`src/app`)에만 둔다.
- 반복되는 UI(버튼, 모달, 입력창)는 `shared/components`에 공통 컴포넌트로 만들어 재사용한다.
- 색상·폰트 등 디자인 기준값은 Tailwind 설정 파일에 모아 정의하고, 컴포넌트에서 임의의 색상 값(`#3a2f1b` 등)을 직접 쓰지 않는다.

---

## API 통신 규칙

### 1. 공통 axios 인스턴스 사용

모든 요청은 `shared/api/client.ts`의 axios 인스턴스로 보낸다. 컴포넌트나 훅에서 `axios`나 `fetch`를 직접 호출하지 않는다.

공통 인스턴스가 담당하는 것:

- API 기본 주소(`VITE_API_BASE_URL`) 설정
- 액세스 토큰 자동 첨부
- `401 EXPIRED_TOKEN`일 때만 재발급 후 원래 요청을 한 번 재전송. 다른 401은 자동 재발급하지 않음
- 성공 시 원래 axios 응답(`status`, `headers`, `data`) 유지. 도메인 API 함수가 `response.data.data`를 꺼냄
- 실패 시 HTTP 상태·`message`·`data.code`·`data.detail`을 보존한 공통 에러로 변환

```ts
// shared/api/types.ts - 백엔드 CommonResponse와 같은 모양
export type CommonResponse<T> = {
  success: boolean;
  message: string;
  data: T;
  timestamp: string;
};

// 실패 응답의 data
export type ErrorData = {
  code: string; // 백엔드 ErrorCode 이름 (docs/api/errors.md)
  detail: unknown;
};
```

- 에러 분기(토큰 재발급, 노트 충돌, 연결 교체 등)는 `data.code`로 한다. `message` 문자열을 비교해 분기하지 않는다.
- 조작 API에는 `X-Connection-Id` 헤더를 공통 인스턴스에서 붙인다. 대상 API는 [docs/api/README.md](../api/README.md#x-connection-id-조작-연결-식별자)를 따른다.

### 2. API 함수는 도메인별 `api.ts`에 작성

```ts
// features/npc/api.ts
// 처리 중 응답 타입은 docs/api/room.md의 AiRequestStatusResponse를 사용한다.
export async function askNpc(roomId: number, suspectId: number, request: NpcAskRequest) {
  const response = await apiClient.post<CommonResponse<NpcAskResponse | AiRequestStatusResponse>>(
    `/api/rooms/${roomId}/npc/${suspectId}/ask`,
    request,
  );

  const data = response.data.data;
  if (response.status === 202 && 'status' in data && data.status === 'PROCESSING') {
    return { kind: 'processing' as const, data };
  }
  if (response.status === 200 && 'answer' in data) {
    return { kind: 'completed' as const, data };
  }
  throw new Error('서버 응답 형식이 예상과 다릅니다.');
}
```

- API 함수 이름은 백엔드 Controller 메서드명과 맞춘다. (`askNpc` ↔ `NpcController.askNpc`)
- 컴포넌트에서는 API 함수를 직접 부르지 않고, TanStack Query 훅(`useQuery`/`useMutation`)으로 감싼 커스텀 훅을 사용한다.

### 3. 에러 메시지 표시

- 사용자에게 보여줄 에러 문구는 백엔드 응답의 `message`를 그대로 사용한다. 프론트엔드에서 같은 의미의 문구를 새로 만들지 않는다.
- 네트워크 오류 등 백엔드 응답이 없는 경우에만 프론트엔드 공통 문구를 사용한다.

### 4. 백엔드 완성 전 개발: 목(mock) 응답

백엔드 API가 아직 없으면 MSW로 사전 설계 명세와 같은 모양의 가짜 응답을 만들어 개발한다.

- 목 핸들러는 `src/mocks/handlers/{도메인}.ts`에 도메인별로 둔다. 요청·응답 타입은 실제 API와 같은 타입을 사용한다.
- 목은 개발 모드에서 환경 변수(`VITE_USE_MOCK=true`)를 켰을 때만 동작하게 하고, 배포 빌드에는 포함하지 않는다.
- 정상 응답뿐 아니라 명세에 정의된 오류(`409` 충돌, `202` 처리 중, AI 실패 등)도 목으로 재현할 수 있게 만든다.
- 목 데이터에는 가상의 사건만 사용한다. ([common.md](common.md#정답-정보-보호-공개-저장소) 참고)
- 실제 백엔드 API가 완성되면 해당 목을 끄고 실제 서버와 연결해 확인한다. 동작이 다르면 명세를 기준으로 어느 쪽이 틀렸는지 판단한다.

### 5. AI 요청의 `requestId` 처리

NPC 질문·힌트·보충 질문·지목과 관리자 제작 채팅은 중복 처리를 막기 위해 `requestId`를 함께 보낸다.

- 사용자가 **새로 전송**할 때 `crypto.randomUUID()`로 새 `requestId`를 만든다.
- 실패 후 **재시도**할 때는 새로 만들지 않고, 처음 만든 `requestId`와 같은 입력을 그대로 다시 보낸다. 입력을 고쳐서 보내면 새 요청이므로 새 `requestId`를 만든다.
- `202 Accepted`이면 결과 대기 상태로 표시한다. 게임 요청은 `GET /api/rooms/{roomId}/ai-requests/{requestId}`, 관리자 제작 채팅은 `GET /api/admin/cases/{caseId}/messages`에서 해당 `requestId`의 상태로 확인한다. 완료·실패·화면 이탈 시 조회 반복을 해제한다.
- AI API 함수는 `response.status`로 완료 결과와 처리 중 응답을 구분하는 타입을 반환한다. 202의 `data`를 완료 응답 타입으로 단정하지 않는다.
- 요청이 처리 중인 동안에는 전송 버튼을 비활성화한다.
- 실패 시 "힌트 횟수는 차감되지 않았습니다"처럼 기회가 소모되지 않았음을 안내하고, 같은 요청을 다시 보내는 버튼을 제공한다.
- 재접속 시에는 서버가 복원해 준 처리 중·실패 요청을 화면에 다시 표시한다. `requestId`를 브라우저에만 보관한다고 가정하지 않는다.
- 남은 힌트·지목 횟수는 **사용 완료**와 **처리 중(임시 확보)**을 구분해 표시한다.
- 채점 실패 시 "같은 답안 다시 채점" 버튼은 서버의 최소 재시도 간격(잠정 10초)이 지나기 전에는 비활성화하고, "나중에 다시 시도"로 메인 화면에 나갈 수 있게 한다. 자동으로 반복 재시도하지 않는다.

### 6. 보안

- Claude API 키 등 비밀 값은 프론트엔드에 절대 두지 않는다. 프론트엔드 코드와 `VITE_` 환경 변수는 빌드 결과에 그대로 포함되어 누구나 볼 수 있다.
- 원작·범인·트릭 등 정답 정보는 백엔드가 결과 공개 전에 내려주지 않는다. 프론트엔드는 받지 않은 정답 정보를 추측하거나 하드코딩하지 않는다.
- 버튼을 숨기거나 비활성화하는 것은 사용 편의를 위한 것이며 보안 수단이 아니다. 권한·상태 검사는 백엔드가 한다.

### 7. 인증 정보 보관

- 액세스 토큰은 메모리(Zustand)에만, 리프레시 토큰은 `localStorage`에 보관한다. 전달·갱신 방식은 [docs/api/auth-user.md](../api/auth-user.md#토큰-전달보관갱신)를 따르며 README에도 기록한다. 정해진 방식 외의 저장 위치를 임의로 추가하지 않는다.
- `connectionId`는 브라우저 저장소에 두지 않고 메모리에만 둔다. 새로고침하면 일시정지 → 이어하기로 새로 발급받는다.
- 새로고침 후 로그인 상태 복원은 앱 시작 시 한 곳(`app`의 인증 초기화)에서만 처리한다.
- 동시에 여러 요청이 만료되면 하나의 재발급 Promise를 함께 기다린다. 로그인·재발급 요청 자체에는 재발급 처리를 적용하지 않으며 원래 요청도 한 번만 재전송해 무한 반복을 막는다.
- 재전송 시 원래 본문·`requestId`·연결 식별자를 유지한다. 인증 실패 재시도와 새 AI 실행 요청을 혼동하지 않는다.
- 로그아웃·사용자 변경 시 이전 요청의 구독·조회 작업을 정리하고 Query 캐시 및 사용자·세션 메모리 상태를 제거한다. 늦은 이전 사용자 응답이 캐시나 토큰을 복원하지 않도록 확인한다. 개인 노트의 로컬 초안은 사용자별 키로 분리하며 다른 사용자에게 표시하지 않는다.

---

## React와 Phaser의 역할 분리

React와 Phaser는 각자 맡은 일만 하고, `EventBus`를 통해서만 대화한다. 이벤트 목록은 기획서 5.2절의 연동 계약을 따른다.

| 담당 | React | Phaser |
|---|---|---|
| 그리는 것 | 사건 카드, 오른쪽 패널(대화·대화 기록·물증 설명·힌트·사전 정보), 개인 노트, 지목 창, 타이머·남은 횟수, 랭킹 | 맵, 캐릭터, NPC·물증 오브젝트, "E: 대화"/"E: 조사" 안내 |
| 하는 일 | API 호출, 서버 상태 표시, 열린 창 관리, 입력 가능 여부 결정 | 이동(WASD·방향키), 충돌, 상호작용 거리 판정, E키 감지 |
| 하지 않는 일 | 캐릭터 좌표 계산, 거리 판정 | API 호출, 창 열기·닫기, 입력 가능 여부 스스로 결정 |

### 이벤트 계약

| 방향 | 이벤트 | 데이터 | 의미 |
|---|---|---|---|
| Phaser → React | `INTERACT_REQUESTED` | `{ type: 'SUSPECT' \| 'EVIDENCE', targetId }` | E키로 선택한 NPC·물증. React가 대화창을 열거나 조사 API를 호출 |
| Phaser → React | `MAP_READY` | 없음 | 맵·에셋 준비 완료. React가 시작/이어하기 버튼을 활성화 |
| React → Phaser | `SET_INPUT_ENABLED` | `{ enabled }` | 이동·상호작용 입력 허용 여부 |
| React → Phaser | `PLACE_AT_SPAWN` | 없음 | 캐릭터를 사건 시작점에 배치 (최초 진입, 재접속) |

```ts
// game/events.ts
export const GameEvents = {
  INTERACT_REQUESTED: 'INTERACT_REQUESTED',
  MAP_READY: 'MAP_READY',
  SET_INPUT_ENABLED: 'SET_INPUT_ENABLED',
  PLACE_AT_SPAWN: 'PLACE_AT_SPAWN',
} as const;

export type GameEventPayloads = {
  [GameEvents.INTERACT_REQUESTED]: { type: 'SUSPECT' | 'EVIDENCE'; targetId: number };
  [GameEvents.MAP_READY]: void;
  [GameEvents.SET_INPUT_ENABLED]: { enabled: boolean };
  [GameEvents.PLACE_AT_SPAWN]: void;
};
```

### 흐름 예시: NPC에게 말 걸기

```plain text
[Phaser] NPC 상호작용 범위 안에 들어감 → "E: 대화" 안내 표시 (창을 자동으로 열지 않음)
[Phaser] E키 입력 → emit(INTERACT_REQUESTED, { type: 'SUSPECT', targetId })
[React]  오른쪽 패널에 NPC 대화창 열기 → 열린 창이 생겼으므로 emit(SET_INPUT_ENABLED, { enabled: false })
[React]  질문 전송 시 API 호출 (Phaser는 API를 호출하지 않음)
[React]  Esc 또는 닫기 → 아래 공통 입력 허용 조건을 만족하면 emit(SET_INPUT_ENABLED, { enabled: true })
```

### 규칙

- 이벤트 이름과 데이터 타입은 `game/events.ts` 한 곳에만 정의하고, 문자열을 코드 곳곳에 직접 쓰지 않는다. 이벤트를 추가·변경하면 기획서 5.2절과 맞춰 변경을 기록하고, 기존 구독자가 수정 없이 동작하지 못하는 경우에만 Breaking Change로 표시한다.
- Phaser 코드(`src/game`)에서 API를 호출하거나 React 상태를 직접 바꾸지 않는다.
- React 컴포넌트에서 Phaser Scene 내부 객체를 직접 조작하지 않는다.
- React 컴포넌트에서 이벤트를 구독했다면, 컴포넌트가 사라질 때 반드시 구독을 해제한다. 해제하지 않으면 같은 이벤트가 여러 번 처리된다.
- 게임 화면을 벗어나면 Phaser 게임 인스턴스를 종료(`destroy`)한다.
- 맵 위 캐릭터·참가자는 처음부터 배열로 다룬다. MVP에서는 배열 길이가 항상 1이다.
- 플레이어 위치는 서버에 저장하지 않는다. 재접속·새로고침 시에는 항상 `PLACE_AT_SPAWN`으로 시작점에 배치한다.

---

## 조작·창 관리 규칙

기획서 3.9절의 조작 규칙을 구현하기 위한 규칙이다.

- **열린 창 목록은 React가 한 곳에서 관리한다.** (예: `features/room`의 게임 UI 상태) 창을 여닫는 코드가 각 컴포넌트에 흩어지지 않게 한다.
- **입력 허용 조건**: 맵 준비 완료·유효한 조작 연결·열린 창 없음·`IN_PROGRESS`·서버 응답 기반 남은 시간 0 초과를 공통 조건으로 사용한다. 시간 소진 후 채점만 대기하는 경우 `IN_PROGRESS`라도 이동·새 조사를 허용하지 않는다. `READY`/`PAUSED`/종료 및 연결 교체 상태에서도 이동을 막는다. 화면 타이머가 0이면 입력을 잠정 차단하고 서버 상태를 확인하되 클라이언트가 종료 결과를 확정하지 않는다. 남은 시간이 있는 채점 대기는 이동을 추가 제한하지 않고, 중복 지목만 막는다.
- **Esc**: 가장 최근에 연 활성 창 하나만 닫는다.
- **텍스트 입력 중**: 입력창에 포커스가 있으면 WASD·방향키·E가 이동·상호작용을 실행하지 않는다.
- **지목 제출**: 명확한 제출 버튼으로만 전송한다. Enter 키만으로 답안을 제출하지 않는다.
- **창을 닫아도 진행 중인 AI 요청은 취소하지 않는다.** 결과는 다시 창을 열거나 재접속했을 때 볼 수 있다.
- **항상 표시**: 타이머, 남은 힌트 횟수, 남은 지목 횟수는 게임 화면에 항상 보이게 한다.
- 개인 노트는 오른쪽 패널과 동시에 열 수 있다. 대화 기록에서 노트로 내용을 자동 입력하는 기능은 만들지 않는다.

---

## 게임 상태·시간 처리 규칙

- **시간의 기준은 서버다.** 화면의 타이머는 서버가 알려준 남은 시간으로 표시만 하고, 시간 만료 여부를 프론트엔드가 판정하지 않는다.
- 하트비트 응답·상태 조회 응답을 받을 때마다 타이머를 서버 값으로 다시 맞춘다.
- 하트비트는 `useHeartbeat` 훅 하나에서만 보낸다. 창이 열려 있거나, 입력 중이거나, AI 응답을 기다리는 중에도 멈추지 않고 보낸다. 탭이 백그라운드로 가도 일부러 멈추지 않는다.
- 세션 상태·남은 시간·처리 중 요청에 따라 가능한 행동이 다르다. 버튼 활성화 여부는 서버가 준 정보를 공통 조건으로 계산하고, 서버에서도 한 번 더 검증된다는 전제로 작성한다.
- **입장·재접속 순서**: 상태 조회로 대화·힌트·노트·처리 중 요청 복원 → 맵 로딩 → `PLACE_AT_SPAWN` → `MAP_READY` 수신 후에 "수사 시작"(`READY`) 또는 "수사 이어하기"(`PAUSED`) 버튼을 활성화한다.
- 새로고침 후 복원 화면에서는 서버에 일시정지를 요청한 뒤 "수사 이어하기"를 표시한다.
- 메인 화면의 본인 세션 목록에서 진행·일시정지 세션은 "이어하기"(기존 `roomId` 사용, 새 방 생성 금지), 종료 세션은 "결과 보기"로 연결한다.
- "저장 후 나가기"는 노트 저장이 완료된 것을 확인한 뒤 일시정지를 요청한다.

### 개인 노트 저장

- 입력이 멈추고 잠시 뒤 자동 저장하며, 저장 중·완료·실패 상태를 화면에 표시한다.
- 자동 저장 요청은 한 번에 하나만 보낸다. 앞선 저장이 끝나기 전에 입력이 바뀌면, 끝난 뒤 최신 내용으로 한 번 더 저장한다.
- 저장 시 마지막으로 받은 `revision`을 함께 보낸다. `409` 충돌이면 서버 내용으로 덮어쓰지 않고, 서버 내용과 로컬 초안을 모두 보여준다.
- 저장되지 않은 입력은 사용자·세션별 키로 브라우저 저장소(`localStorage`)에 임시 보관한다. 서버 저장에 실패하거나 시간 종료·이탈로 저장이 거부되면 "이 기기의 미저장 초안"으로 구분해 다시 열고 복사할 수 있게 한다.
- 노트 창을 닫을 때 미저장 내용을 저장하며, 실패해도 입력 내용을 버리지 않는다.
- 자동 저장 간격·글자 수 제한 등 잠정값은 `shared/constants`에 상수로 두고, 기획서의 해당 절을 주석으로 남긴다.

---

## 에셋 관리

- 게임 에셋은 `public/assets/` 하위에 종류별로 두고 빌드와 함께 배포한다. (`maps/`, `tilesets/`, `sprites/`)
- 파일명은 kebab-case 영어로 작성한다. (예: `mansion-interior.json`, `suspect-butler.png`)
- NPC 스프라이트는 `sprite_key`로 참조한다. 사용 가능한 키와 파일 경로는 `public/assets/manifest.json` 한 곳에서 관리하고 `game/assets.ts`는 이 파일을 읽어 사용한다. 백엔드도 출시 검사에서 같은 파일로 키를 검증한다. 이미 사건 데이터에서 쓰는 키·경로를 제거하거나 호환되지 않게 바꾸면 Breaking Change로 표시한다. 호환되는 키 추가는 일반 변경으로 기록한다. 플레이어는 공통 스프라이트 한 종을 사용한다.
- **`public/` 폴더의 파일은 누구나 내려받을 수 있다.** 맵 JSON·에셋 메타데이터에는 범인 여부, 정답, NPC 내부 설정, 물증 설명, 원작 제목을 넣지 않는다. 공개용 배치 식별자와 렌더링 정보만 넣는다.
- 외부 에셋을 추가하면 `public/assets/CREDITS.md`에 아래 항목을 반드시 기록한다. 라이선스나 사용 조건을 확인할 수 없는 에셋은 사용하지 않는다.
  - 원본 출처 URL, 제작자, 정확한 라이선스와 버전
  - 수정 여부, 필요한 고지와 배포 조건
  - LPC 캐릭터 생성기처럼 여러 부품을 합성한 경우 부품별 출처와 조건
- 사건 카드 표지 이미지는 관리자가 업로드하여 백엔드가 제공하므로, 프론트엔드 저장소에 넣지 않는다.
- 지원 환경은 PC 웹 브라우저(키보드·마우스)다. 모바일 대응은 MVP에서 만들지 않는다.

---

## 테스트 코드 작성 규칙

- 테스트 파일은 대상 파일 옆에 `{파일명}.test.ts(x)`로 둔다.
- 다음은 반드시 테스트를 작성한다.
  - `utils`의 함수 (예: 남은 시간 표시 형식 변환)
  - 상태를 계산하는 커스텀 훅 (예: 버튼 활성화 조건, 재시도 시 `requestId` 재사용)
  - 입력 허용 조건 (맵 준비·연결·열린 창·세션 상태·남은 시간 → `SET_INPUT_ENABLED` 값)
  - 노트 자동 저장 (한 번에 하나만 전송, `409` 충돌 시 로컬 초안 보존)
- 화면 모양과 실제 조작(이동, E키, Esc, 창과 노트 병행 사용)은 직접 플레이해서 확인하고, PR에 화면 캡처를 첨부한다.
- 테스트 내부는 백엔드와 같이 `// given` / `// when` / `// then`으로 구분한다.
- 테스트 이름은 한국어로 동작을 설명한다.

```ts
describe('formatRemainingTime', () => {
  it('남은 시간을 분:초 형식으로 변환한다', () => {
    // given
    const remainingMs = 125_000;

    // when
    const result = formatRemainingTime(remainingMs);

    // then
    expect(result).toBe('02:05');
  });
});
```

---

## 용어 설명

프론트엔드가 처음인 사람을 위한 설명입니다. 괄호 안은 백엔드에서 비슷한 개념입니다.

| 용어 | 설명 |
|---|---|
| 컴포넌트 | 화면의 한 조각을 그리는 함수. 버튼, 대화창, 페이지 모두 컴포넌트다 |
| Props | 부모 컴포넌트가 자식 컴포넌트에게 넘겨주는 값 (메서드 파라미터) |
| State | 컴포넌트가 기억하는 값. 바뀌면 화면이 다시 그려진다 |
| 훅(Hook) | `use`로 시작하는 함수. 상태·API 호출 같은 로직을 컴포넌트 밖으로 빼서 재사용한다 (Service) |
| TanStack Query | 서버 데이터를 가져오고 캐시·재시도·로딩 상태를 관리해주는 도구 |
| Zustand | 여러 화면이 함께 쓰는 값을 담아두는 전역 저장소 |
| Scene | Phaser의 화면 단위. 에셋 로딩 Scene, 수사 맵 Scene 등으로 나눈다 |
| EventBus | React와 Phaser가 서로 신호를 주고받는 통로 (이벤트 발행·구독) |
| Vite | 개발 서버를 띄우고, 배포용 파일을 만들어주는 빌드 도구 (Gradle) |
| npm | 라이브러리 설치·관리 도구, `package.json`에 의존성을 기록한다 (Gradle 의존성 관리) |
