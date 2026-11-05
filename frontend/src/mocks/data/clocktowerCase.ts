import type { CaseCardResponse } from '../../features/cases/types';
import type { RoomBriefingResponse, RoomMapResponse } from '../../features/room/types';

// 목에서 쓰는 가상 사건 데이터. 설명용으로 지어낸 내용이며 실제 소설·원작과 관련이 없다.
// 첫 번째 사건은 백엔드 테스트 픽스처(ClocktowerCaseFixture)의 공개 정보(표시명·태그·사전 정보·인물·물증)만 옮겼다.
// 범인·트릭·채점 기준·인물 내부 설정은 2주차 API에 필요 없으므로 넣지 않는다.
// 설정값(제한시간·힌트 횟수 등)은 backend application.yml의 shelved.game 잠정값과 같다.

export type MockEvidence = {
  evidenceId: number;
  name: string;
  /** 조사 API(inspect) 응답으로만 내려주는 설명 */
  description: string;
  /** 임시 좌표. 실제 맵 에셋(AS-1)이 만들어지면 맞춘다 */
  x: number;
  y: number;
};

export type MockSuspect = {
  suspectId: number;
  name: string;
  /** 임시 키. 실제 스프라이트 에셋(AS-1)이 만들어지면 맞춘다 */
  spriteKey: string | null;
  canConverse: boolean;
  /** 미배치 인물은 null */
  x: number | null;
  y: number | null;
};

export type MockCase = {
  card: CaseCardResponse;
  briefing: RoomBriefingResponse;
  map: Omit<RoomMapResponse, 'suspects' | 'evidences'>;
  suspects: MockSuspect[];
  evidences: MockEvidence[];
};

/** 난이도별 설정값 (backend shelved.game, 잠정) */
export const MOCK_GAME_CONFIG = {
  timeLimitSec: { EASY: 1200, NORMAL: 1500, HARD: 2100 },
  hintLimit: { EASY: 5, NORMAL: 3, HARD: 1 },
  accusationLimit: 3,
  heartbeatIntervalMs: 5000,
  heartbeatTimeoutMs: 15000,
  noteMaxLength: 5000,
} as const;

const CLOCKTOWER_CASE: MockCase = {
  card: {
    caseId: 1,
    displayTitle: '시계탑 저택의 마지막 종소리',
    difficulty: 'EASY',
    keywordTags: ['저택', '밀실', '알리바이'],
    // 표지 이미지 에셋이 없으므로 null로 두어 기본 표시를 확인한다
    coverImageUrl: null,
    timeLimitSec: MOCK_GAME_CONFIG.timeLimitSec.EASY,
    hintLimit: MOCK_GAME_CONFIG.hintLimit.EASY,
    accusationLimit: MOCK_GAME_CONFIG.accusationLimit,
  },
  briefing: {
    displayTitle: '시계탑 저택의 마지막 종소리',
    difficulty: 'EASY',
    introduction: '시계탑이 있는 저택에서 주인이 서재에서 숨진 채 발견되었다.',
    investigationGoal: '범인과 범행 방법을 밝혀라.',
    backgroundKnowledge: '사건 당시 저택에는 집사·가정부·정원사가 있었다.',
    timeLimitSec: MOCK_GAME_CONFIG.timeLimitSec.EASY,
    hintLimit: MOCK_GAME_CONFIG.hintLimit.EASY,
    accusationLimit: MOCK_GAME_CONFIG.accusationLimit,
  },
  map: {
    mapUrl: '/assets/maps/clocktower-mansion.json',
    spawnX: 112,
    spawnY: 368,
    collisionLayer: 'collision',
    interactionRadiusPx: 48,
  },
  suspects: [
    // spriteKey는 assets/manifest.json의 키
    {
      suspectId: 1,
      name: '해리스',
      spriteKey: 'npc-gentleman-01',
      canConverse: true,
      x: 368,
      y: 208,
    },
    {
      suspectId: 2,
      name: '마거릿',
      spriteKey: 'npc-maid-01',
      canConverse: true,
      x: 656,
      y: 208,
    },
    { suspectId: 3, name: '톰', spriteKey: 'npc-workman-01', canConverse: true, x: 432, y: 560 },
    // 대화 불가·미배치 인물. 지목 대상에는 포함된다
    { suspectId: 4, name: '엘리스', spriteKey: null, canConverse: false, x: null, y: null },
  ],
  evidences: [
    {
      evidenceId: 1,
      name: '서재의 작은 상자',
      description: '책장 뒤에서 발견된 손바닥만 한 나무 상자. 안쪽에 태엽 장치가 들어 있다.',
      x: 176,
      y: 176,
    },
    {
      evidenceId: 2,
      name: '찢긴 메모',
      description: "서재 휴지통에서 나온 메모 조각. '9시 15분'이라는 글자만 알아볼 수 있다.",
      x: 848,
      y: 208,
    },
  ],
};

// 카드 목록과 "진행 중" 배지를 비교해 볼 수 있도록 둔 두 번째 가상 사건. 세부 데이터는 최소한만 둔다.
const LIGHTHOUSE_CASE: MockCase = {
  card: {
    caseId: 2,
    displayTitle: '안개 낀 등대의 빈 의자',
    difficulty: 'NORMAL',
    keywordTags: ['등대', '안개'],
    coverImageUrl: null,
    timeLimitSec: MOCK_GAME_CONFIG.timeLimitSec.NORMAL,
    hintLimit: MOCK_GAME_CONFIG.hintLimit.NORMAL,
    accusationLimit: MOCK_GAME_CONFIG.accusationLimit,
  },
  briefing: {
    displayTitle: '안개 낀 등대의 빈 의자',
    difficulty: 'NORMAL',
    introduction: '안개가 짙은 밤, 등대지기의 의자만 비어 있었다.',
    investigationGoal: '무슨 일이 있었는지 밝혀라.',
    backgroundKnowledge: '등대에는 등대지기와 보급선 선원이 머물고 있었다.',
    timeLimitSec: MOCK_GAME_CONFIG.timeLimitSec.NORMAL,
    hintLimit: MOCK_GAME_CONFIG.hintLimit.NORMAL,
    accusationLimit: MOCK_GAME_CONFIG.accusationLimit,
  },
  map: {
    mapUrl: '/assets/maps/clocktower-mansion.json',
    spawnX: 112,
    spawnY: 368,
    collisionLayer: 'collision',
    interactionRadiusPx: 48,
  },
  suspects: [
    {
      suspectId: 11,
      name: '보급선 선원',
      spriteKey: 'npc-old-man-01',
      canConverse: true,
      x: 368,
      y: 208,
    },
  ],
  evidences: [
    {
      evidenceId: 11,
      name: '식은 찻잔',
      description: '탁자 위에 놓인 찻잔. 이미 차갑게 식어 있다.',
      x: 176,
      y: 176,
    },
  ],
};

/** 목이 아는 모든 사건. caseId 순서 = 카드 목록 순서 */
export const MOCK_CASES: MockCase[] = [CLOCKTOWER_CASE, LIGHTHOUSE_CASE];

export function findMockCase(caseId: number): MockCase | undefined {
  return MOCK_CASES.find((mockCase) => mockCase.card.caseId === caseId);
}
