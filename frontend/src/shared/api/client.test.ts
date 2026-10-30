import { CanceledError } from 'axios';
import { delay, http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { mockErrorResponse } from '../../mocks/handlers/auth';
import { server } from '../../mocks/node';
import { ApiError, NETWORK_ERROR_MESSAGE } from './apiError';
import {
  apiClient,
  resetApiSession,
  setAccessTokenGetter,
  setConnectionId,
  setTokenRefresher,
} from './client';
import type { CommonResponse, ErrorData } from './types';

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

/** 요청 헤더를 그대로 돌려주는 테스트용 핸들러 */
function echoHeaders(path: string) {
  return http.all(`${API_BASE_URL}${path}`, ({ request }) =>
    HttpResponse.json({
      authorization: request.headers.get('Authorization'),
      connectionId: request.headers.get('X-Connection-Id'),
    }),
  );
}

async function captureError(request: Promise<unknown>): Promise<unknown> {
  try {
    await request;
  } catch (error) {
    return error;
  }
  throw new Error('요청이 실패하지 않았습니다.');
}

afterEach(() => {
  setAccessTokenGetter(null);
  setTokenRefresher(null);
  setConnectionId(null);
});

describe('apiClient 성공 응답', () => {
  it('axios 응답의 status와 공통 응답 본문을 그대로 돌려준다', async () => {
    // given
    server.use(
      http.post(`${API_BASE_URL}/api/rooms/1/npc/2/ask`, () =>
        HttpResponse.json(
          {
            success: true,
            message: '요청 처리 중',
            data: { requestId: 'req-1', status: 'PROCESSING' },
            timestamp: '2026-10-05T14:30:00.123',
          },
          { status: 202 },
        ),
      ),
    );

    // when
    const response = await apiClient.post<CommonResponse<{ requestId: string; status: string }>>(
      '/api/rooms/1/npc/2/ask',
      { requestId: 'req-1', question: '어젯밤 서재에 있었나요?' },
    );

    // then
    expect(response.status).toBe(202);
    expect(response.data.data).toEqual({ requestId: 'req-1', status: 'PROCESSING' });
  });
});

describe('apiClient 요청 본문', () => {
  it('일반 객체 요청은 JSON으로 보낸다', async () => {
    // given
    server.use(
      http.patch(`${API_BASE_URL}/api/users/me/nickname`, async ({ request }) =>
        HttpResponse.json({
          contentType: request.headers.get('Content-Type'),
          body: await request.json(),
        }),
      ),
    );

    // when
    const response = await apiClient.patch('/api/users/me/nickname', { nickname: '탐정' });

    // then
    expect(response.data.contentType).toContain('application/json');
    expect(response.data.body).toEqual({ nickname: '탐정' });
  });

  it('FormData 요청은 JSON으로 바꾸지 않고 multipart로 보낸다', async () => {
    // given
    server.use(
      // jsdom이 만든 multipart 본문은 MSW(undici)의 request.formData()가 해석하지 못해 원문 텍스트로 확인한다
      http.post(`${API_BASE_URL}/api/admin/cases/1/source-text`, async ({ request }) =>
        HttpResponse.json({
          contentType: request.headers.get('Content-Type'),
          body: await request.text(),
        }),
      ),
    );
    const formData = new FormData();
    formData.append('file', new File(['가상의 원문 내용'], 'sample.txt', { type: 'text/plain' }));

    // when
    const response = await apiClient.post('/api/admin/cases/1/source-text', formData);

    // then
    expect(response.data.contentType).toMatch(/^multipart\/form-data; boundary=/);
    expect(response.data.body).toContain('name="file"; filename="sample.txt"');
    expect(response.data.body).toContain('가상의 원문 내용');
  });
});

describe('apiClient 실패 응답', () => {
  it('백엔드 실패 응답을 상태·message·code·detail을 보존한 ApiError로 바꾼다', async () => {
    // given
    const body: CommonResponse<ErrorData> = {
      success: false,
      message:
        '다른 곳에서 노트가 먼저 저장되었습니다. 서버 내용과 작성 중인 내용을 확인해 주세요.',
      data: {
        code: 'NOTE_REVISION_CONFLICT',
        detail: { currentRevision: 8, currentContent: '서버에 저장된 노트 내용' },
      },
      timestamp: '2026-10-05T14:30:00.123',
    };
    server.use(
      http.put(`${API_BASE_URL}/api/rooms/1/notes/me`, () =>
        HttpResponse.json(body, { status: 409 }),
      ),
    );

    // when
    const error = await captureError(apiClient.put('/api/rooms/1/notes/me', { content: '메모' }));

    // then
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(409);
    expect(apiError.message).toBe(body.message);
    expect(apiError.code).toBe('NOTE_REVISION_CONFLICT');
    expect(apiError.detail).toEqual({
      currentRevision: 8,
      currentContent: '서버에 저장된 노트 내용',
    });
    expect(apiError.isNetworkError).toBe(false);
  });

  it('detail이 없는 실패 응답은 detail을 null로 둔다', async () => {
    // given
    server.use(
      http.get(`${API_BASE_URL}/api/users/me`, () =>
        HttpResponse.json(
          {
            success: false,
            message: '로그인이 만료되었습니다.',
            data: { code: 'EXPIRED_TOKEN', detail: null },
            timestamp: '2026-10-05T14:30:00.123',
          },
          { status: 401 },
        ),
      ),
    );

    // when
    const error = (await captureError(apiClient.get('/api/users/me'))) as ApiError;

    // then
    expect(error.status).toBe(401);
    expect(error.code).toBe('EXPIRED_TOKEN');
    expect(error.detail).toBeNull();
  });

  it('응답이 없는 네트워크 오류는 isNetworkError로 구분하고 공통 문구를 쓴다', async () => {
    // given
    server.use(http.get(`${API_BASE_URL}/api/cases`, () => HttpResponse.error()));

    // when
    const error = await captureError(apiClient.get('/api/cases'));

    // then
    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.isNetworkError).toBe(true);
    expect(apiError.status).toBeNull();
    expect(apiError.code).toBeNull();
    expect(apiError.message).toBe(NETWORK_ERROR_MESSAGE);
  });
});

describe('apiClient X-Connection-Id 헤더', () => {
  it('연결 헤더가 필요하다고 표시한 요청에만 X-Connection-Id를 붙인다', async () => {
    // given
    server.use(echoHeaders('/api/rooms/1/heartbeat'), echoHeaders('/api/rooms/1/state'));
    setConnectionId('conn-abc');

    // when
    const marked = await apiClient.post('/api/rooms/1/heartbeat', null, {
      requiresConnectionId: true,
    });
    const unmarked = await apiClient.get('/api/rooms/1/state');

    // then
    expect(marked.data.connectionId).toBe('conn-abc');
    expect(unmarked.data.connectionId).toBeNull();
  });

  it('보관 중인 connectionId가 없으면 표시한 요청에도 헤더를 붙이지 않는다', async () => {
    // given
    server.use(echoHeaders('/api/rooms/1/heartbeat'));

    // when
    const response = await apiClient.post('/api/rooms/1/heartbeat', null, {
      requiresConnectionId: true,
    });

    // then
    expect(response.data.connectionId).toBeNull();
  });
});

describe('apiClient Authorization 헤더', () => {
  it('토큰 getter가 등록되지 않았으면 Authorization을 붙이지 않는다', async () => {
    // given
    server.use(echoHeaders('/api/users/me'));

    // when
    const response = await apiClient.get('/api/users/me');

    // then
    expect(response.data.authorization).toBeNull();
  });

  it('토큰 getter가 등록되면 Bearer 토큰을 붙인다', async () => {
    // given
    server.use(echoHeaders('/api/users/me'));
    setAccessTokenGetter(() => 'access-token-123');

    // when
    const response = await apiClient.get('/api/users/me');

    // then
    expect(response.data.authorization).toBe('Bearer access-token-123');
  });

  it('토큰 getter가 null을 돌려주면 Authorization을 붙이지 않는다', async () => {
    // given
    server.use(echoHeaders('/api/users/me'));
    setAccessTokenGetter(() => null);

    // when
    const response = await apiClient.get('/api/users/me');

    // then
    expect(response.data.authorization).toBeNull();
  });
});

describe('apiClient 토큰 재발급', () => {
  const EXPIRED = () => mockErrorResponse(401, 'EXPIRED_TOKEN', '로그인이 만료되었습니다.');

  /** 메모리 토큰을 흉내 내고, 재발급하면 'new-token'으로 바꾸는 테스트용 재발급 함수를 등록한다 */
  function setupTokens(beforeRefresh?: () => void) {
    let accessToken = 'old-token';
    setAccessTokenGetter(() => accessToken);
    const refresher = vi.fn(async () => {
      beforeRefresh?.();
      await delay(10);
      accessToken = 'new-token';
      return accessToken;
    });
    setTokenRefresher(refresher);
    return refresher;
  }

  /** 새 토큰이면 성공하고, 아니면 401 EXPIRED_TOKEN을 돌려주는 핸들러 */
  function expiresUnlessNewToken(path: string) {
    return http.get(`${API_BASE_URL}${path}`, ({ request }) =>
      request.headers.get('Authorization') === 'Bearer new-token'
        ? HttpResponse.json({ success: true, message: '성공', data: path, timestamp: '' })
        : EXPIRED(),
    );
  }

  it('401 EXPIRED_TOKEN이면 재발급 후 새 토큰으로 원래 요청을 다시 보낸다', async () => {
    // given
    const refresher = setupTokens();
    server.use(expiresUnlessNewToken('/api/users/me'));

    // when
    const response = await apiClient.get('/api/users/me');

    // then
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(response.status).toBe(200);
    expect(response.data.data).toBe('/api/users/me');
  });

  it('EXPIRED_TOKEN이 아닌 401은 재발급하지 않고 ApiError로 실패한다', async () => {
    // given
    const refresher = setupTokens();
    server.use(
      http.get(`${API_BASE_URL}/api/users/me`, () =>
        mockErrorResponse(401, 'INVALID_TOKEN', '유효하지 않은 토큰입니다.'),
      ),
    );

    // when
    const error = (await captureError(apiClient.get('/api/users/me'))) as ApiError;

    // then
    expect(refresher).not.toHaveBeenCalled();
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('INVALID_TOKEN');
  });

  it('동시에 만료된 요청 여러 개는 재발급을 한 번만 하고 함께 기다린다', async () => {
    // given
    const refresher = setupTokens();
    server.use(
      expiresUnlessNewToken('/api/users/me'),
      expiresUnlessNewToken('/api/rooms'),
      expiresUnlessNewToken('/api/rooms/1/state'),
    );

    // when
    const responses = await Promise.all([
      apiClient.get('/api/users/me'),
      apiClient.get('/api/rooms'),
      apiClient.get('/api/rooms/1/state'),
    ]);

    // then
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(responses.map((response) => response.data.data)).toEqual([
      '/api/users/me',
      '/api/rooms',
      '/api/rooms/1/state',
    ]);
  });

  it('재전송한 요청이 다시 만료되면 또 재발급하지 않고 실패한다', async () => {
    // given
    const refresher = setupTokens();
    let requestCount = 0;
    server.use(
      http.get(`${API_BASE_URL}/api/users/me`, () => {
        requestCount += 1;
        return EXPIRED();
      }),
    );

    // when
    const error = (await captureError(apiClient.get('/api/users/me'))) as ApiError;

    // then
    expect(refresher).toHaveBeenCalledTimes(1);
    expect(requestCount).toBe(2);
    expect(error.code).toBe('EXPIRED_TOKEN');
  });

  it('재전송할 때 원래 본문·requestId·X-Connection-Id를 그대로 보낸다', async () => {
    // given
    setConnectionId('conn-original');
    // 재발급 사이에 연결 식별자가 바뀌어도 재전송은 처음 보낸 값을 유지해야 한다
    setupTokens(() => setConnectionId('conn-changed'));
    const received: { authorization: string | null; connectionId: string | null; body: unknown }[] =
      [];
    server.use(
      http.post(`${API_BASE_URL}/api/rooms/1/npc/2/ask`, async ({ request }) => {
        received.push({
          authorization: request.headers.get('Authorization'),
          connectionId: request.headers.get('X-Connection-Id'),
          body: await request.json(),
        });
        return received.length === 1
          ? EXPIRED()
          : HttpResponse.json({ success: true, message: '성공', data: null, timestamp: '' });
      }),
    );
    const requestBody = { requestId: 'req-123', question: '어젯밤 서재에 있었나요?' };

    // when
    await apiClient.post('/api/rooms/1/npc/2/ask', requestBody, { requiresConnectionId: true });

    // then
    expect(received).toHaveLength(2);
    expect(received[1].body).toEqual(requestBody);
    expect(received[1].connectionId).toBe('conn-original');
    expect(received[0].authorization).toBe('Bearer old-token');
    expect(received[1].authorization).toBe('Bearer new-token');
  });

  it('재발급이 실패하면 원래 요청은 재발급 실패 ApiError로 실패한다', async () => {
    // given
    setAccessTokenGetter(() => 'old-token');
    setTokenRefresher(async () => {
      throw new ApiError({
        status: 401,
        message: '로그인 정보가 만료되었습니다. 다시 로그인해 주세요.',
        code: 'INVALID_REFRESH_TOKEN',
        detail: null,
        isNetworkError: false,
      });
    });
    server.use(expiresUnlessNewToken('/api/users/me'));

    // when
    const error = (await captureError(apiClient.get('/api/users/me'))) as ApiError;

    // then
    expect(error).toBeInstanceOf(ApiError);
    expect(error.code).toBe('INVALID_REFRESH_TOKEN');
  });

  it('로그인·재발급·로그아웃처럼 skipAuth로 표시한 요청은 토큰을 붙이지 않고 재발급하지 않는다', async () => {
    // given
    const refresher = setupTokens();
    let authorization: string | null = 'not-called';
    server.use(
      http.post(`${API_BASE_URL}/api/auth/refresh`, ({ request }) => {
        authorization = request.headers.get('Authorization');
        return EXPIRED();
      }),
    );

    // when
    const error = (await captureError(
      apiClient.post('/api/auth/refresh', { refreshToken: 'r' }, { skipAuth: true }),
    )) as ApiError;

    // then
    expect(authorization).toBeNull();
    expect(refresher).not.toHaveBeenCalled();
    expect(error.code).toBe('EXPIRED_TOKEN');
  });

  it('API 세션을 초기화(로그아웃)하기 전에 보낸 요청의 늦은 응답은 취소로 버린다', async () => {
    // given
    let markArrived: () => void = () => {};
    const arrived = new Promise<void>((resolve) => {
      markArrived = resolve;
    });
    server.use(
      http.get(`${API_BASE_URL}/api/rooms`, async () => {
        markArrived();
        await delay(20);
        return HttpResponse.json({ success: true, message: '성공', data: [], timestamp: '' });
      }),
    );
    const request = apiClient.get('/api/rooms');
    await arrived;

    // when
    resetApiSession();
    const error = await captureError(request);

    // then
    expect(error).toBeInstanceOf(CanceledError);
  });
});
