/// <reference types="vitest/config" />
import { rm } from 'node:fs/promises';
import { resolve } from 'node:path';
import tailwindcss from '@tailwindcss/vite';
import react from '@vitejs/plugin-react';
import { defineConfig, type Plugin } from 'vite';

/**
 * MSW 워커 파일(public/mockServiceWorker.js)은 개발 서버에서만 필요하므로 배포 빌드 결과에서 지운다.
 * public 폴더의 파일은 빌드 시 dist로 그대로 복사되기 때문에 별도로 제거해야 한다.
 */
function excludeMockServiceWorker(): Plugin {
  let outDir = 'dist';
  return {
    name: 'shelved:exclude-mock-service-worker',
    apply: 'build',
    configResolved(config) {
      outDir = resolve(config.root, config.build.outDir);
    },
    async closeBundle() {
      await rm(resolve(outDir, 'mockServiceWorker.js'), { force: true });
    },
  };
}

const TEST_API_BASE_URL = 'http://localhost:8080';

export default defineConfig({
  plugins: [react(), tailwindcss(), excludeMockServiceWorker()],
  test: {
    environment: 'jsdom',
    setupFiles: ['./src/setupTests.ts'],
    // 개인 .env.local 값과 관계없이 테스트가 같은 기본 주소를 쓰도록 고정한다.
    // jsdom 페이지 주소도 같은 origin으로 맞춰, 브라우저처럼 CORS 사전 요청(OPTIONS)을 보내지 않게 한다.
    env: { VITE_API_BASE_URL: TEST_API_BASE_URL },
    environmentOptions: { jsdom: { url: TEST_API_BASE_URL } },
  },
});
