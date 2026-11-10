import { http, HttpResponse } from 'msw';
import { setupServer } from 'msw/node';
import { handlers } from './handlers';

// 정적 파일(/assets/manifest.json)은 실제 서버가 주지만 테스트에는 서버가 없으므로 스프라이트 없는 빈 manifest를 준다.
// 얼굴 그림 자체는 manifest를 직접 넘겨 테스트하고, 여기서는 불러오기 요청이 막히지 않게만 한다.
const EMPTY_WALK = {
  frameCount: 9,
  frameRate: 8,
  idleFrame: 0,
  rows: { up: 0, left: 1, down: 2, right: 3 },
};
const testManifest = {
  version: 1,
  player: {
    path: '/assets/sprites/player.png',
    frameWidth: 64,
    frameHeight: 64,
    animations: { walk: EMPTY_WALK },
  },
  sprites: {},
  maps: {},
};

/** 테스트(Vitest)에서 쓰는 MSW 서버. src/setupTests.ts가 모든 테스트 전에 시작한다 */
export const server = setupServer(
  ...handlers,
  http.get('*/assets/manifest.json', () => HttpResponse.json(testManifest)),
);
