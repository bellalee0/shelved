// 모든 테스트 파일보다 먼저 실행되는 공통 설정 (vite.config.ts의 test.setupFiles)
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterAll, afterEach, beforeAll } from 'vitest';
import { resetMockAuthDb } from './mocks/handlers/auth';
import { server } from './mocks/node';

// 목 핸들러가 없는 요청은 실수로 실제 네트워크에 나가지 않도록 테스트를 실패시킨다
beforeAll(() => server.listen({ onUnhandledFrame: 'error' }));

afterEach(() => {
  cleanup();
  // 테스트 안에서 server.use()로 추가한 핸들러를 지워 다음 테스트에 영향이 없게 한다
  server.resetHandlers();
  resetMockAuthDb();
  localStorage.clear();
});

afterAll(() => server.close());
