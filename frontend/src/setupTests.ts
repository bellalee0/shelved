// 모든 테스트 파일보다 먼저 실행되는 공통 설정 (vite.config.ts의 test.setupFiles)
import '@testing-library/jest-dom/vitest';
import { cleanup } from '@testing-library/react';
import { afterEach } from 'vitest';

afterEach(() => {
  cleanup();
});
