import { setupServer } from 'msw/node';
import { handlers } from './handlers';

/** 테스트(Vitest)에서 쓰는 MSW 서버. src/setupTests.ts가 모든 테스트 전에 시작한다 */
export const server = setupServer(...handlers);
