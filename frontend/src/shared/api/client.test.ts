import { http, HttpResponse } from 'msw';
import { afterEach, describe, expect, it } from 'vitest';
import { server } from '../../mocks/node';
import { ApiError, NETWORK_ERROR_MESSAGE } from './apiError';
import { apiClient, setAccessTokenGetter, setConnectionId } from './client';
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
