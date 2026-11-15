import { describe, expect, it } from 'vitest';
import type { AiRequestStatusResponse } from '../../shared/api/types';
import { joinConversations, mergeNpcMessages, pickPendingRequest } from './messages';
import type { NpcMessageResponse } from './types';

function messageOf(messageId: number, sequenceNo: number): NpcMessageResponse {
  return {
    messageId,
    senderType: sequenceNo % 2 === 1 ? 'USER' : 'NPC',
    content: `메시지 ${messageId}`,
    sequenceNo,
    createdAt: '2026-11-12T10:00:00',
  };
}

function requestOf(
  requestId: string,
  status: AiRequestStatusResponse['status'],
  acceptedAt: string,
): AiRequestStatusResponse {
  return {
    requestId,
    type: 'NPC_ASK',
    targetId: 1,
    status,
    input: { question: '질문' },
    result: null,
    error: null,
    retryAvailableAt: null,
    acceptedAt,
    updatedAt: acceptedAt,
  };
}

describe('mergeNpcMessages', () => {
  it('같은 messageId는 한 번만 두고 sequenceNo 순으로 정렬한다', () => {
    // given
    const existing = [messageOf(11, 1), messageOf(12, 2)];
    const added = [messageOf(14, 4), messageOf(12, 2), messageOf(13, 3)];

    // when
    const merged = mergeNpcMessages(existing, added);

    // then
    expect(merged.map((message) => message.messageId)).toEqual([11, 12, 13, 14]);
  });
});

describe('pickPendingRequest', () => {
  it('처리 중인 요청이 있으면 그것을 고른다', () => {
    // given
    const requests = [
      requestOf('failed', 'FAILED', '2026-11-12T10:05:00'),
      requestOf('processing', 'PROCESSING', '2026-11-12T10:01:00'),
    ];

    // when
    const picked = pickPendingRequest(requests);

    // then
    expect(picked?.requestId).toBe('processing');
  });

  it('실패 요청만 있으면 가장 최근에 접수된 것을 고르고, 없으면 null이다', () => {
    // given
    const requests = [
      requestOf('older', 'FAILED', '2026-11-12T10:01:00'),
      requestOf('newer', 'FAILED', '2026-11-12T10:03:00'),
    ];

    // when
    const picked = pickPendingRequest(requests);

    // then
    expect(picked?.requestId).toBe('newer');
    expect(pickPendingRequest([])).toBeNull();
  });
});

describe('joinConversations', () => {
  it('맵 이름을 suspectId로 붙이고 최근 대화 순으로 정렬한다', () => {
    // given
    const conversations = [
      { suspectId: 1, messageCount: 2, lastMessageAt: '2026-11-12T10:01:00' },
      { suspectId: 3, messageCount: 4, lastMessageAt: '2026-11-12T10:09:00' },
      { suspectId: 9, messageCount: 2, lastMessageAt: '2026-11-12T10:05:00' },
    ];
    const suspects = [
      { suspectId: 1, name: '해리스' },
      { suspectId: 3, name: '톰' },
    ];

    // when
    const items = joinConversations(conversations, suspects);

    // then
    expect(items.map((item) => item.name)).toEqual(['톰', '인물', '해리스']);
    expect(items[0]).toMatchObject({ suspectId: 3, messageCount: 4 });
  });
});
