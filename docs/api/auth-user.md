# 인증 · 사용자 API

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 6.1절, 7.1절 기준입니다.

| Method | 경로 | 설명 | 인증 |
|---|---|---|---|
| `POST` | `/api/auth/google` | Google ID 토큰으로 로그인, 토큰 발급 | 불필요 |
| `POST` | `/api/auth/refresh` | 리프레시 토큰으로 토큰 재발급 | 불필요 (리프레시 토큰) |
| `POST` | `/api/auth/logout` | 리프레시 토큰 무효화 | 불필요 (리프레시 토큰) |
| `GET` | `/api/users/me` | 내 정보 조회 (새로고침 후 로그인 복원) | 필요 |
| `PATCH` | `/api/users/me/nickname` | 닉네임 설정·변경 | 필요 |

---

## 토큰 처리 흐름

### 로그인

```plain text
[브라우저] Google 로그인 버튼(Google Identity Services) 클릭 → Google이 ID 토큰(credential) 반환
[프론트]   POST /api/auth/google { idToken }
[백엔드]   Google 공개키로 ID 토큰 검증(발급자, 대상 client ID, 만료) → google_sub로 사용자 조회·생성
[백엔드]   accessToken(1시간) + refreshToken(2주) 발급, refreshToken은 해시로 REFRESH_TOKENS에 저장
[프론트]   user.nicknameRequired=true 이면 닉네임 설정 화면으로 이동
```

- Google 로그인 화면으로의 리다이렉트·callback 처리를 백엔드가 하지 않습니다. 프론트엔드가 Google에서 받은 **ID 토큰**만 백엔드로 보내고, 백엔드는 검증만 합니다. Google client secret이 필요 없고 백엔드에 OAuth 세션 상태가 남지 않습니다.
- 필요한 환경 변수: 백엔드 `GOOGLE_CLIENT_ID`, 프론트엔드 `VITE_GOOGLE_CLIENT_ID` (같은 값)

### 토큰 전달·보관·갱신

| 항목 | 방식 |
|---|---|
| 액세스 토큰 전달 | 모든 인증 API에 `Authorization: Bearer {accessToken}` 헤더 |
| 액세스 토큰 보관 | 프론트엔드 메모리(Zustand). 새로고침하면 사라진다 |
| 리프레시 토큰 보관 | `localStorage` |
| 액세스 토큰 만료 | API가 `401 EXPIRED_TOKEN` → 공통 axios 인스턴스가 `/api/auth/refresh` 호출 → 원래 요청 재시도 (동시에 여러 요청이 만료되어도 재발급은 한 번만) |
| 재발급 | 리프레시 토큰도 새로 발급(rotation)하고 이전 리프레시 토큰은 무효화 |
| 새로고침 후 복원 | 앱 시작 시 `localStorage`에 리프레시 토큰이 있으면 `/api/auth/refresh` → `/api/users/me` 순서로 로그인 상태 복원 |
| 재발급 실패 | `401 INVALID_REFRESH_TOKEN` → 아래 탭 간 조정 규칙으로 최신 토큰 재확인. 같은 토큰이 여전히 저장되어 있을 때만 삭제 후 로그인 화면 |
| 로그아웃 | `/api/auth/logout` 호출 후 메모리·`localStorage`의 토큰 삭제 |

> 액세스 토큰을 `localStorage`에 두지 않고 메모리에만 두는 이유: 수명이 짧은 액세스 토큰은 새로고침 시 리프레시 토큰으로 다시 받으면 되므로, 브라우저 저장소에 남는 토큰을 리프레시 토큰 하나로 줄입니다.

---

### 여러 탭의 토큰 갱신 조정

- 동일 탭의 재발급은 하나의 Promise를 공유합니다. 탭 간에는 같은 origin의 Web Locks API(`shelved-auth` 잠금)로 로그인 토큰 저장·재발급·로그아웃을 직렬화합니다. MVP는 이 API를 지원하는 PC 보안 컨텍스트(HTTPS 또는 localhost)를 대상으로 합니다.
- 잠금 획득 후 `localStorage`의 최신 리프레시 토큰을 다시 읽고 재발급합니다. 새 토큰은 잠금을 해제하기 전에 저장합니다. 각 탭의 액세스 토큰은 계속 메모리에만 둡니다. 다른 탭이 먼저 갱신한 경우에도 이 탭은 최신 리프레시 토큰으로 순차 재발급해 자신의 액세스 토큰을 얻습니다.
- 서버는 이전 토큰 무효화와 새 토큰 저장을 한 트랜잭션에서 원자적으로 수행해 같은 토큰의 중복 rotation을 허용하지 않습니다.
- `INVALID_REFRESH_TOKEN`이면 요청에 사용한 토큰과 현재 저장값을 비교합니다. 값이 달라졌다면 새 값을 삭제하지 않고 최신 값으로 한 번만 재시도합니다. 같으면 삭제하고 로그인 화면으로 이동합니다. 네트워크·서버 오류에서는 토큰을 삭제하지 않고 재시도 안내를 표시합니다.
- `storage` 이벤트로 다른 탭의 토큰 삭제(로그아웃)를 감지하면 메모리 인증 상태도 비웁니다. 로그아웃도 잠금 안에서 최신 토큰을 읽어 무효화하고 저장값을 삭제합니다.

## POST /api/auth/google

Google ID 토큰을 검증해 로그인합니다. 처음 로그인한 사용자는 이 요청에서 생성되며 `role=PLAYER`, `nickname=null`입니다.

**요청 본문** `AuthGoogleLoginRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `idToken` | string | O | Google Identity Services가 반환한 credential |

**성공 응답** `200` `AuthTokenResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `accessToken` | string | |
| `refreshToken` | string | |
| `accessTokenExpiresInSec` | number | `3600` |
| `user` | [UserMeResponse](#usermeresponse) | 로그인한 사용자 정보 |

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | `idToken` 누락 |
| 401 | `GOOGLE_TOKEN_INVALID` | ID 토큰 검증 실패(위조·만료·다른 client ID) |

```json
// 응답 예시
{
  "success": true,
  "message": "로그인 성공",
  "data": {
    "accessToken": "eyJhbGciOi...",
    "refreshToken": "eyJhbGciOi...",
    "accessTokenExpiresInSec": 3600,
    "user": { "userId": 7, "nickname": null, "role": "PLAYER", "nicknameRequired": true }
  },
  "timestamp": "2026-10-05T14:30:00.123"
}
```

---

## POST /api/auth/refresh

**요청 본문** `AuthRefreshRequest`

| 필드 | 타입 | 필수 |
|---|---|---|
| `refreshToken` | string | O |

**성공 응답** `200` `AuthTokenResponse` (로그인과 같은 형식, 새 리프레시 토큰 포함)

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 401 | `INVALID_REFRESH_TOKEN` | 존재하지 않음·만료·무효화된 리프레시 토큰 |

---

## POST /api/auth/logout

리프레시 토큰을 무효화(`revoked_at` 기록)합니다. 액세스 토큰이 이미 만료된 상태에서도 로그아웃할 수 있도록 `Authorization` 헤더를 요구하지 않습니다.

**요청 본문** `AuthLogoutRequest`

| 필드 | 타입 | 필수 |
|---|---|---|
| `refreshToken` | string | O |

**성공 응답** `200`, `data: null`

- 이미 무효화됐거나 존재하지 않는 토큰이어도 `200`을 반환합니다(로그아웃은 결과가 같으므로).

---

## GET /api/users/me

**성공 응답** `200` `UserMeResponse`

### UserMeResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `userId` | number | |
| `nickname` | string \| null | 미설정 시 `null` |
| `role` | `UserRole` | `PLAYER` / `ADMIN` |
| `nicknameRequired` | boolean | 닉네임 설정이 필요한지 (`nickname == null`) |

---

## PATCH /api/users/me/nickname

최초 설정과 변경에 같은 API를 사용합니다. 랭킹에서 사용자를 구분하는 이름이므로 **중복을 허용하지 않습니다.**

**요청 본문** `UserNicknameUpdateRequest`

| 필드 | 타입 | 필수 | 제약 |
|---|---|---|---|
| `nickname` | string | O | 2~12자, 완성형 한글(`가-힣`)·영문(`A-Z`, `a-z`)·숫자만. 영문 대소문자를 구분하지 않고 중복 검사하며 표시에는 입력 대소문자를 보존. 본인의 기존 닉네임으로 변경 가능 |

**성공 응답** `200` `UserMeResponse`

**실패 응답**

| 상태 | 코드 | 상황 |
|---|---|---|
| 400 | `INVALID_INPUT` | 길이·문자 조건 위반 (`message`에 검증 메시지) |
| 409 | `NICKNAME_EXIST` | 이미 사용 중인 닉네임 |
