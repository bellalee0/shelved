import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { AiRequestStatusResponse } from '../../../shared/api/types';
import type { RoomStateResponse } from '../types';
import { GameHud } from './GameHud';

function pendingRequest(type: AiRequestStatusResponse['type']): AiRequestStatusResponse {
  return {
    requestId: `${type}-1`,
    type,
    targetId: null,
    status: 'PROCESSING',
    input: { question: '질문' },
    result: null,
    error: null,
    retryAvailableAt: null,
    acceptedAt: '2026-11-16T10:00:00',
    updatedAt: '2026-11-16T10:00:00',
  };
}

function roomState(aiRequests: AiRequestStatusResponse[]): RoomStateResponse {
  return {
    roomId: 1,
    caseId: 1,
    displayTitle: '가상의 시계탑 사건',
    difficulty: 'EASY',
    isTest: false,
    startedAt: '2026-11-16T10:00:00',
    timer: {
      status: 'IN_PROGRESS',
      timeLimitSec: 1800,
      elapsedActiveMs: 0,
      remainingMs: 1_800_000,
      awaitingGrading: false,
      serverTime: '2026-11-16T10:00:00',
    },
    heartbeatIntervalMs: 5000,
    hintCount: { limit: 5, used: 0, reserved: 0, remaining: 5 },
    accusationCount: { limit: 3, used: 0, reserved: 0, remaining: 3 },
    pendingAccusation: null,
    aiRequests,
    conversations: [],
  };
}

function renderHud(aiRequests: AiRequestStatusResponse[]) {
  render(
    <GameHud
      roomState={roomState(aiRequests)}
      displayRemainingMs={1_800_000}
      isConnectionUnstable={false}
      onOpenBriefing={() => undefined}
      onOpenConversations={() => undefined}
      onOpenHint={() => undefined}
      onExit={() => undefined}
      actionsEnabled
    />,
  );
}

describe('GameHud 처리 중 표시', () => {
  it('재접속 직후 힌트 요청이 있으면 힌트 버튼에만 표시한다', () => {
    renderHud([pendingRequest('HINT_FOLLOWUP')]);

    expect(screen.getByRole('button', { name: /힌트/ })).toContainElement(
      screen.getByRole('img', { name: '처리 중인 요청 있음' }),
    );
    expect(screen.getAllByRole('img', { name: '처리 중인 요청 있음' })).toHaveLength(1);
  });

  it('요청이 없거나 알 수 없는 유형이면 표시하지 않는다', () => {
    renderHud([pendingRequest('ACCUSATION')]);

    expect(screen.queryByRole('img', { name: '처리 중인 요청 있음' })).not.toBeInTheDocument();
  });

  it('힌트 버튼은 진행 중일 때 활성화된다', () => {
    renderHud([]);

    expect(screen.getByRole('button', { name: '힌트' })).toBeEnabled();
  });
});
