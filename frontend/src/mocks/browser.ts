import { setupWorker } from 'msw/browser';
import { handlers } from './handlers';
import { enableMockAuthDbPersistence } from './handlers/auth';

// 새로고침 후 로그인 복원을 목 모드에서도 확인할 수 있도록, 브라우저에서는 목 사용자·토큰을 저장해 둔다
enableMockAuthDbPersistence();

/** 개발 서버에서 쓰는 MSW 워커. app/main.tsx가 VITE_USE_MOCK=true일 때만 동적 import로 불러온다 */
export const worker = setupWorker(...handlers);
