import { server } from '../node';

// 테스트 전용: 서버로 나간 API 요청을 "METHOD /path?query"로 기록한다. 정리는 테스트의 afterEach에서
// server.events.removeAllListeners()로 한다.

export function recordApiRequests() {
  const requests: string[] = [];
  server.events.on('request:start', ({ request }) => {
    const url = new URL(request.url);
    if (url.pathname.startsWith('/api/')) {
      requests.push(`${request.method} ${url.pathname}${url.search}`);
    }
  });
  return requests;
}
