import type { RoomResultResponse } from '../../features/room/types';

// 목 결과 화면의 리빌. 전부 지어낸 내용이며 실제 소설·원작·사건과 관련이 없다.
// 범인은 mocks/accusationDb.ts의 목 범인, 트릭은 목 채점 키워드("종소리", "시계")와 맞춘다.

export type MockReveal = Pick<
  RoomResultResponse,
  'source' | 'culprit' | 'trickSummary' | 'revealExplanation'
>;

const MOCK_REVEALS: Record<number, MockReveal> = {
  1: {
    source: {
      novelTitle: '(가상) 마지막 종',
      novelAuthor: '(가상) 홍작가',
      novelIntro: '화면 확인을 위해 지어낸 원작 소개입니다. 실제 작품이 아닙니다.',
    },
    culprit: { suspectId: 2, name: '마거릿' },
    trickSummary: '미리 녹음한 종소리로 시계의 시각을 착각하게 했다',
    revealExplanation:
      '마거릿은 시계탑의 종소리를 미리 녹음해 두었습니다.\n\n사람들은 녹음된 소리를 듣고 시계가 그 시각을 가리킨다고 믿었습니다.',
  },
  2: {
    source: {
      novelTitle: '(가상) 안개의 의자',
      novelAuthor: '(가상) 김작가',
      novelIntro: '화면 확인을 위해 지어낸 원작 소개입니다. 실제 작품이 아닙니다.',
    },
    culprit: { suspectId: 11, name: '보급선 선원' },
    trickSummary: '뱃고동을 종소리로 들리게 해 시계의 시각을 속였다',
    revealExplanation:
      '선원은 안개 속에서 뱃고동을 울려 교대 시각을 착각하게 했습니다.\n\n화면 확인을 위해 지어낸 해설입니다.',
  },
};

export function findMockReveal(caseId: number): MockReveal | undefined {
  return MOCK_REVEALS[caseId];
}
