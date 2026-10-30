import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { App } from './App';
import './index.css';

/**
 * 개발 서버이고 VITE_USE_MOCK=true일 때만 MSW를 켠다.
 * import.meta.env.DEV는 배포 빌드에서 false로 바뀌어 이 블록과 동적 import가 통째로 제거되므로 목 코드가 빌드 결과에 들어가지 않는다.
 */
async function enableMocking() {
  if (import.meta.env.DEV && import.meta.env.VITE_USE_MOCK === 'true') {
    const { worker } = await import('../mocks/browser');
    // 목 핸들러가 없는 요청(페이지 리소스, 아직 목을 만들지 않은 API)은 그대로 통과시킨다
    await worker.start({ onUnhandledFrame: 'bypass' });
  }
}

enableMocking().then(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <App />
    </StrictMode>,
  );
});
