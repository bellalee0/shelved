import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';

/** 개발 서버에서 쓰는 MSW 워커. app/main.tsx가 VITE_USE_MOCK=true일 때만 동적 import로 불러온다 */
export const worker = setupWorker(...handlers);
