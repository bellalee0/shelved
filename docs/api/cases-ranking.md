# 사건 카드 · 랭킹 API

> 공통 규칙은 [README.md](README.md)를 따릅니다. 기획서 2.2~2.3절, 3.10절, 7.2절 기준입니다.

| Method | 경로 | 설명 | 인증 |
|---|---|---|---|
| `GET` | `/api/cases` | 공개된 사건 카드 목록 | **불필요** |
| `GET` | `/api/rankings/{caseId}` | 사건별 랭킹 | 필요 |

---

## GET /api/cases

`PUBLISHED` 사건만 반환합니다. 로그인하지 않은 방문자도 메인 화면에서 사건 목록을 볼 수 있습니다. 카드를 선택해 세션을 만들 때 로그인이 필요합니다.

원작 제목·작가 등 정답 정보는 포함하지 않습니다. MVP 사건은 3편이므로 페이지네이션 없이 전체 목록을 반환합니다.

**성공 응답** `200` `List<CaseCardResponse>`

| 필드 | 타입 | 설명 |
|---|---|---|
| `caseId` | number | |
| `displayTitle` | string | 사건 카드 표시명 (원작과 무관한 독자 제목) |
| `difficulty` | `Difficulty` | |
| `keywordTags` | string[] | |
| `coverImageUrl` | string \| null | 표지 이미지 URL ([관리자 표지 업로드](admin-casebuilder.md#post-apiadmincasescaseidcover-image) 참고) |
| `timeLimitSec` | number | 난이도별 기본 제한시간 (설정값) |
| `hintLimit` | number | 난이도별 힌트 횟수 (설정값) |
| `accusationLimit` | number | 지목 기회 (`3`). 카드에 "지목 3회"로 표시 |

```json
{
  "success": true,
  "message": "사건 목록 조회 성공",
  "data": [
    {
      "caseId": 1,
      "displayTitle": "시계탑 저택의 마지막 종소리",
      "difficulty": "EASY",
      "keywordTags": ["저택", "밀실", "알리바이"],
      "coverImageUrl": "/uploads/covers/1-9f2c.webp",
      "timeLimitSec": 1200,
      "hintLimit": 5,
      "accusationLimit": 3
    }
  ],
  "timestamp": "2026-10-05T14:30:00.123"
}
```

---

## GET /api/rankings/{caseId}

사건별로 사용자당 가장 빠른 성공 기록 1개를 순위대로 반환합니다. (기획서 3.10절)

- 집계 대상: `PLAY_RESULTS.cleared=true` 이고 `ROOMS.is_test=false`
- 정렬: `durationSec` 오름차순. 초 단위가 같으면 공동 순위, 다음 순위는 인원만큼 건너뜀 (1·2·2·4)
- 같은 순위 안의 표시 순서는 먼저 달성한 기록(`clearedAt`)이 앞
- 힌트 사용 횟수는 순위에 반영하지 않음

**경로 파라미터**: `caseId` — `PUBLISHED` 사건만 조회 가능. 비공개·존재하지 않는 사건은 `404 CASE_NOT_FOUND`

- 로그인이 필요합니다. 비로그인 방문자가 메인 카드의 "랭킹"을 누르면 프론트엔드가 로그인 화면으로 보냅니다.
- 관리자 테스트 세션의 결과 화면(`result.isTest=true`)은 비공개 사건이라 이 API를 호출하지 않고 "랭킹 미반영"만 표시합니다.

**쿼리 파라미터**: `page`, `size` ([README](README.md#공통-규칙))

**성공 응답** `200` `RankingGetResponse`

| 필드 | 타입 | 설명 |
|---|---|---|
| `caseId` | number | |
| `displayTitle` | string | |
| `myRanking` | [RankingResponse](#rankingresponse) \| null | 요청자의 최고 기록과 순위. 성공 기록이 없으면 `null` |
| `rankingPage` | PageResponse&lt;[RankingResponse](#rankingresponse)&gt; | 기록이 없으면 `content: []` |

### RankingResponse

| 필드 | 타입 | 설명 |
|---|---|---|
| `rank` | number | 공동 순위 적용 |
| `nickname` | string | |
| `durationSec` | number | 일시정지를 제외한 누적 진행 시간 (정답 접수 시점까지) |
| `clearedAt` | string | 정답 제출 접수 시각 |

```json
{
  "success": true,
  "message": "랭킹 조회 성공",
  "data": {
    "caseId": 1,
    "displayTitle": "시계탑 저택의 마지막 종소리",
    "myRanking": { "rank": 2, "nickname": "셜록초보", "durationSec": 912, "clearedAt": "2026-10-04T20:11:03" },
    "rankingPage": {
      "content": [
        { "rank": 1, "nickname": "왓슨", "durationSec": 840, "clearedAt": "2026-10-03T19:02:45" },
        { "rank": 2, "nickname": "셜록초보", "durationSec": 912, "clearedAt": "2026-10-04T20:11:03" },
        { "rank": 2, "nickname": "포와로", "durationSec": 912, "clearedAt": "2026-10-05T10:40:12" },
        { "rank": 4, "nickname": "마플", "durationSec": 1001, "clearedAt": "2026-10-02T22:15:30" }
      ],
      "totalElements": 4,
      "totalPages": 1,
      "size": 20,
      "number": 0
    }
  },
  "timestamp": "2026-10-05T14:30:00.123"
}
```
