# 사건 제작 초안(draft) 구조와 AI tool 정의

> 관리자 제작 채팅에서 AI가 채우고, 관리자 화면의 "메모장" 패널이 그리는 초안 JSON의 구조입니다. 기획서 4.2절, 4.5절, 6.3절 기준입니다.
> 이 구조는 세 곳이 함께 사용합니다: `CASE_DRAFTS.draft_json`(DB), 관리자 API 응답의 `draft`, 제작 채팅의 AI tool 인자.
> 예시는 모두 가상의 사건입니다.

## 목차

1. [전체 구조](#전체-구조)
2. [항목별 필드](#항목별-필드)
3. [키(key)와 엔티티 ID](#키key와-엔티티-id)
4. [AI tool 정의](#ai-tool-정의)
5. [콘텐츠 확정 시 검증과 반영](#콘텐츠-확정-시-검증과-반영)

---

## 전체 구조

```json
{
  "schemaVersion": 1,
  "card": { "displayTitle": null, "difficulty": null, "keywordTags": [] },
  "source": { "novelTitle": null, "novelAuthor": null, "novelIntro": null },
  "briefing": { "introduction": null, "investigationGoal": null, "backgroundKnowledge": null },
  "suspects": [],
  "evidences": [],
  "trick": { "summary": null, "elements": [] },
  "revealExplanation": null,
  "glossary": []
}
```

새 사건을 만들면 위의 빈 초안이 저장됩니다. 값이 정해지지 않은 필드는 `null` 또는 `[]`입니다.

| 항목 | 메모장 패널 영역 | 확정 시 반영 위치 |
|---|---|---|
| `card` | 카드 정보 | `CASES.display_title`, `difficulty`, `keyword_tags` |
| `source` | 원작 정보 | `CASES.source_novel_title`, `source_novel_author`, `source_novel_intro` |
| `briefing` | 사전 정보 | `CASES.introduction`, `investigation_goal`, `background_knowledge` |
| `suspects` | 등장인물 / NPC 설정 | `SUSPECTS` (`persona`는 `persona_prompt`로 변환), `CASES.culprit_suspect_id` |
| `evidences` | 물증 | `EVIDENCES` |
| `trick` | 트릭 · 판정 기준 | `CASES.trick_summary`, `CASE_TRICK_ELEMENTS` |
| `revealExplanation` | 리빌 해설 | `CASES.reveal_explanation` |
| `glossary` | 고유명사 용어집 | `CASES.glossary` |

---

## 항목별 필드

### 공통 타입

**SourceRef** — 설정의 근거가 되는 원문 위치

| 필드 | 타입 | 설명 |
|---|---|---|
| `location` | string | 장·문단 등 원문 위치 (예: `"3장 12문단"`) |
| `quote` | string | 근거 원문 인용 (원문 언어 그대로) |

**Fact** — 근거가 붙은 설정 한 줄

| 필드 | 타입 |
|---|---|
| `text` | string |
| `sourceRefs` | SourceRef[] |

### card

| 필드 | 타입 | 설명 |
|---|---|---|
| `displayTitle` | string \| null | 사건 카드 표시명 (원작과 무관한 제목) |
| `difficulty` | `Difficulty` \| null | |
| `keywordTags` | string[] | 1~5개 |

### source

| 필드 | 타입 | 설명 |
|---|---|---|
| `novelTitle` | string \| null | |
| `novelAuthor` | string \| null | |
| `novelIntro` | string \| null | 리빌 화면의 원작 소개 (2~4문장, 발표 시기·작품 특징 등). 사건 해설은 `revealExplanation`에 따로 쓴다 |

### briefing

| 필드 | 타입 | 설명 |
|---|---|---|
| `introduction` | string \| null | 사건 소개 |
| `investigationGoal` | string \| null | 수사 목표 |
| `backgroundKnowledge` | string \| null | 난이도에 맞춘 기본 배경지식 |

### suspects[]

| 필드 | 타입 | 설명 |
|---|---|---|
| `key` | string | 초안 안의 고정 키 (예: `"sus-1"`). [키 규칙](#키key와-엔티티-id) |
| `entityId` | number \| null | 확정 후 연결된 `SUSPECTS.id`. 확정 전 `null` |
| `name` | string | 한국어 표기 (용어집을 따름) |
| `role` | string | 인물 개요 (관리자 검수용, 플레이어 비공개) |
| `canConverse` | boolean | 대화 가능 여부. `false`여도 지목 대상에는 포함 |
| `isCulprit` | boolean | 범인 여부. 초안 전체에서 정확히 1명 |
| `spriteKey` | string \| null | 정적 에셋 목록의 스프라이트 식별자 |
| `persona` | [Persona](#persona) | NPC 지식·진술·추궁 방침. `canConverse=false`면 비워둘 수 있음 |

#### Persona

| 필드 | 타입 | 설명 |
|---|---|---|
| `speechStyle` | string | 말투 |
| `knownFacts` | Fact[] | 이 인물이 아는 사실 |
| `unknownFacts` | Fact[] | 이 인물이 모르는 사실 (전지적 답변 방지) |
| `statements` | Fact[] | 기본 진술 |
| `hiddenFacts` | Fact[] | 숨기는 사실 |
| `lies` | object[] | 정해진 거짓말 `{ "topic", "lie", "truth", "sourceRefs" }` |
| `silenceTopics` | Fact[] | 답변을 거부·침묵하는 주제 |
| `pressureResponse` | string | 추궁에 대한 기본 반응 (예: "모순을 추궁받아도 자백하지 않고 답변을 거부한다") |
| `statementChanges` | object[] | 사건 성립에 필수적인 진술 변경 조건 `{ "condition", "change", "sourceRefs" }`. 없으면 `[]` |

### evidences[]

| 필드 | 타입 | 설명 |
|---|---|---|
| `key` | string | 예: `"evi-1"` |
| `entityId` | number \| null | 확정 후 `EVIDENCES.id` |
| `name` | string | |
| `description` | string | 플레이어가 조사 시 보는 설명 (검수된 고정 텍스트) |
| `sourceRefs` | SourceRef[] | |

### trick

| 필드 | 타입 | 설명 |
|---|---|---|
| `summary` | string \| null | 트릭 요약 (리빌에서 공개) |
| `elements` | TrickElement[] | 정답 핵심 요소. 3~5개 권장 |

#### TrickElement

| 필드 | 타입 | 설명 |
|---|---|---|
| `key` | string | 예: `"trk-1"` |
| `entityId` | number \| null | 확정 후 `CASE_TRICK_ELEMENTS.id` |
| `orderIndex` | number | 표시·판정 순서 (1부터) |
| `description` | string | 핵심 요소 설명 |
| `rubric` | [Rubric](#rubric) | 판정 기준. 확정 시 `grading_rubric`에 저장 |
| `supportingClues` | object[] | 이 요소를 자백 없이 추론할 수 있게 하는 단서 `{ "type": "EVIDENCE" \| "TESTIMONY", "refKey", "note" }`. `refKey`는 물증 키 또는 인물 키 (관리자 검수용, 확정 후 게임에서 사용하지 않음) |

#### Rubric

| 필드 | 타입 | 설명 |
|---|---|---|
| `items` | object[] | 필수 판정 항목 (1개 이상). 아래 표 |
| `easyFeedback` | object | 쉬움 난이도 피드백 참고 `{ "guideQuestion", "evidenceKeys": [], "suspectKeys": [] }` |

`items[]`

| 필드 | 타입 | 설명 |
|---|---|---|
| `itemId` | string | 요소 안의 고정 ID (예: `"trk-1-i1"`). 채점 결과(`element_results`)가 이 ID를 참조 |
| `label` | string | 항목 이름 (예: "사용 도구", "사용 목적") |
| `criterion` | string | 충족 기준 (의미 기반) |
| `acceptExamples` | string[] | 인정 예시 |
| `rejectExamples` | string[] | 불인정 예시 (`INCORRECT` 예) |

### glossary[]

| 필드 | 타입 | 설명 |
|---|---|---|
| `original` | string | 원문 표기 (고유 키) |
| `korean` | string | 고정할 한국어 표기 |
| `note` | string \| null | 설명 |

### 예시 (일부)

```json
{
  "schemaVersion": 1,
  "card": { "displayTitle": "시계탑 저택의 마지막 종소리", "difficulty": "EASY", "keywordTags": ["저택", "알리바이"] },
  "suspects": [
    {
      "key": "sus-1",
      "entityId": null,
      "name": "해리스",
      "role": "저택의 집사. 20년째 근무",
      "canConverse": true,
      "isCulprit": true,
      "spriteKey": "npc-butler-01",
      "persona": {
        "speechStyle": "정중하고 짧게 말한다",
        "knownFacts": [{ "text": "서재에 녹음기가 있다는 것을 안다", "sourceRefs": [{ "location": "2장 4문단", "quote": "..." }] }],
        "unknownFacts": [],
        "statements": [{ "text": "9시에는 주방에서 은식기를 닦고 있었다", "sourceRefs": [] }],
        "hiddenFacts": [{ "text": "8시 50분에 서재에 들어갔다", "sourceRefs": [] }],
        "lies": [{ "topic": "9시의 행적", "lie": "주방에 있었다", "truth": "서재에서 녹음기를 설치했다", "sourceRefs": [] }],
        "silenceTopics": [],
        "pressureResponse": "모순을 추궁받아도 자백하지 않고 답변을 거부한다",
        "statementChanges": []
      }
    }
  ],
  "evidences": [
    { "key": "evi-1", "entityId": null, "name": "서재의 작은 상자", "description": "책장 뒤에서 발견된 손바닥만 한 나무 상자. 안쪽에 태엽 장치가 들어 있다.", "sourceRefs": [] }
  ],
  "trick": {
    "summary": "녹음기로 피해자가 살아 있는 시각을 조작했다",
    "elements": [
      {
        "key": "trk-1",
        "entityId": null,
        "orderIndex": 1,
        "description": "녹음기를 서재에 설치해 피해자가 살아 있는 시각을 조작했다",
        "rubric": {
          "items": [
            { "itemId": "trk-1-i1", "label": "사용 도구", "criterion": "녹음 장치를 사용했음을 밝힘", "acceptExamples": ["녹음기", "소리를 재생하는 장치"], "rejectExamples": [] },
            { "itemId": "trk-1-i2", "label": "사용 목적", "criterion": "피해자가 살아 있는 시각을 늦춰 보이게 함", "acceptExamples": ["사망 시각을 속였다"], "rejectExamples": ["비명을 가렸다"] }
          ],
          "easyFeedback": { "guideQuestion": "사람들이 피해자의 목소리를 언제 들었다고 했나요?", "evidenceKeys": ["evi-1"], "suspectKeys": ["sus-1"] }
        },
        "supportingClues": [{ "type": "EVIDENCE", "refKey": "evi-1", "note": "태엽 장치가 녹음기의 흔적" }]
      }
    ]
  },
  "glossary": [{ "original": "Harris", "korean": "해리스", "note": "집사" }]
}
```

---

## 키(key)와 엔티티 ID

- `key`(`sus-N`, `evi-N`, `trk-N`)와 `itemId`(`trk-N-iM`)는 **서버가 항목 추가 시 발급**합니다. AI나 프론트엔드가 만들지 않습니다.
- 한 사건 안에서 번호는 증가만 하고 삭제된 키를 재사용하지 않습니다.
- `update_*`/`remove_*` tool과 참조 필드(`supportingClues.refKey`, `easyFeedback.evidenceKeys` 등)는 이 키를 사용합니다.
- 콘텐츠 확정 시 서버가 각 항목의 `entityId`를 채웁니다. 재확정할 때 `entityId`가 있는 항목은 **같은 DB 행을 갱신**하여 ID를 보존하고(기획서 4.4절), 초안에서 삭제된 항목의 행은 삭제합니다.

---

## AI tool 정의

제작 채팅에서 Claude가 호출할 수 있는 tool 목록입니다. 백엔드 `casebuilder` 도메인에 정의합니다.

| tool | 인자 | 동작 |
|---|---|---|
| `set_card_info` | `displayTitle?`, `difficulty?`, `keywordTags?` | 준 필드만 교체 |
| `set_source_info` | `novelTitle?`, `novelAuthor?`, `novelIntro?` | 준 필드만 교체 |
| `set_briefing` | `introduction?`, `investigationGoal?`, `backgroundKnowledge?` | 준 필드만 교체 |
| `add_suspect` | `name`, `role`, `canConverse`, `isCulprit`, `spriteKey?`, `persona?` | 추가 후 발급된 `key` 반환 |
| `update_suspect` | `key` + 바꿀 필드 | 준 필드만 교체 (`persona`는 그 안의 준 필드만 교체) |
| `remove_suspect` | `key` | 삭제 |
| `add_evidence` | `name`, `description`, `sourceRefs?` | 추가 후 `key` 반환 |
| `update_evidence` | `key` + 바꿀 필드 | |
| `remove_evidence` | `key` | |
| `set_trick_summary` | `summary` | |
| `add_trick_element` | `description`, `orderIndex?`, `rubric`, `supportingClues?` | 추가 후 `key`, `itemId` 목록 반환 |
| `update_trick_element` | `key` + 바꿀 필드 | `rubric.items`를 바꾸면 기존 `itemId`는 유지하고 새 항목만 새 ID 발급 (항목 식별은 `itemId`) |
| `remove_trick_element` | `key` | |
| `set_reveal_explanation` | `revealExplanation` | |
| `set_glossary_term` | `original`, `korean`, `note?` | `original` 기준 추가 또는 교체 |
| `remove_glossary_term` | `original` | |

### 공통 동작

- **배열 필드는 통째로 교체**합니다(`keywordTags`, `knownFacts`, `acceptExamples` 등). 일부만 바꾸려면 AI가 전체 배열을 다시 보냅니다.
- 한 번의 채팅 요청 안에서 AI는 여러 tool을 연달아 호출할 수 있습니다. 서버는 각 호출을 **메모리 속 초안 사본**에 적용하고 `tool_result`로 성공(발급된 키 포함) 또는 오류 사유를 돌려줍니다. AI가 응답을 마치면 대화 내용과 변경된 초안을 **한 번에 저장**합니다. 도중에 AI 호출이 실패하면 아무것도 저장하지 않습니다.
- tool 인자 검증 실패(없는 키, 타입 오류, 존재하지 않는 참조, 다른 항목이 참조 중인 항목 삭제)는 채팅 전체를 실패시키지 않고 해당 tool의 오류 `tool_result`로 AI에게 알려 AI가 고치게 합니다.
- 사용 가능한 `spriteKey` 목록과 현재 초안·용어집은 매 요청의 입력에 포함합니다.

### 관리자 화면에 보여줄 변경 요약 (ToolChange)

채팅 응답과 채팅 이력의 AI 메시지에 포함됩니다. 성공한 tool 호출만 기록합니다.

| 필드 | 타입 | 설명 |
|---|---|---|
| `tool` | string | 예: `update_suspect` |
| `targetKey` | string \| null | 대상 키 (`set_*`는 `null`, 용어집은 `original`) |
| `summary` | string | 서버가 만든 한 줄 요약 (예: "인물 sus-1 persona.lies 변경") |

---

## 콘텐츠 확정 시 검증과 반영

`POST /api/admin/cases/{caseId}/confirm`에서 서버가 검사합니다. `ERROR`가 하나라도 있으면 확정하지 않고 `409 DRAFT_INVALID`와 목록을 반환합니다. `WARNING`은 확정을 막지 않습니다.

같은 검사 결과를 확정 전에도 볼 수 있도록 `GET /api/admin/cases/{caseId}`와 `POST /api/admin/cases/{caseId}/chat` 응답의 `draftIssues`로 함께 반환합니다. 관리자 화면은 메모장 옆에 이 목록을 항상 표시합니다.

**DraftIssue**

| 필드 | 타입 | 설명 |
|---|---|---|
| `severity` | `ERROR` \| `WARNING` | |
| `path` | string | 문제 위치 (예: `"suspects[sus-2].spriteKey"`, `"trick.elements"`) |
| `message` | string | 한국어 설명 |

| 검사 | 심각도 |
|---|---|
| `card`·`source`·`briefing`·`trick.summary`·`revealExplanation` 필드가 비어 있음 | ERROR |
| `keywordTags`가 0개 또는 6개 이상 | ERROR |
| 인물 0명, 범인(`isCulprit`)이 정확히 1명이 아님 | ERROR |
| 대화 가능 인물의 `persona`에 `statements`·`pressureResponse`가 비어 있음 | ERROR |
| 물증 0개, 물증 `description`이 비어 있음 | ERROR |
| 트릭 요소 0개 또는 6개 이상, 요소에 `rubric.items`가 없음 | ERROR |
| 존재하지 않는 키 참조 (`supportingClues`, `easyFeedback`) | ERROR |
| 트릭 요소가 1~2개 | WARNING (3~5개 권장) |
| 트릭 요소에 `supportingClues`가 없음 (자백 없이 추론 가능한지 확인 필요) | WARNING |
| 설정 항목에 `sourceRefs`가 없음 | WARNING |
| `spriteKey`가 없거나 에셋 목록에 없음 | WARNING (출시 검사에서 ERROR) |

**반영 방식**

- `persona`는 서버가 `src/main/resources/prompts/`의 템플릿으로 텍스트로 변환해 `SUSPECTS.persona_prompt`에 저장합니다. 원작 근거(`sourceRefs`)와 `supportingClues`는 초안에만 남깁니다(관리자 검수용).
- `rubric`은 `CASE_TRICK_ELEMENTS.grading_rubric`(jsonb)에 그대로 저장하되 `evidenceKeys`/`suspectKeys`는 엔티티 ID로 바꿔 저장합니다.
- 삭제된 인물·물증을 가리키던 맵 배치(`MAP_HOTSPOTS`)는 제거하고 응답의 `removedHotspots`로 알립니다.
