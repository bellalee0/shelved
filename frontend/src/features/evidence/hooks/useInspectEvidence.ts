import { useMutation } from '@tanstack/react-query';
import { isApiError } from '../../../shared/api/apiError';
import { inspectEvidence } from '../api';

/** 세션 상태 때문에 조작이 거부된 응답 (docs/api/investigation.md "세션 상태에 따른 공통 실패") */
function isSessionRejection(error: unknown): error is { code: string } {
  return (
    isApiError(error) &&
    error.code !== null &&
    (error.status === 409 || error.code === 'CONNECTION_ID_REQUIRED')
  );
}

/**
 * 물증 조사(POST inspect). 같은 물증은 몇 번이든 다시 조사할 수 있다.
 * 세션 상태 오류(409·연결 없음)면 onSessionRejected로 코드를 알린다 — 게임 화면이 상태를 다시 확인한다.
 */
export function useInspectEvidence(roomId: number, onSessionRejected: (code: string) => void) {
  return useMutation({
    mutationFn: (evidenceId: number) => inspectEvidence(roomId, evidenceId),
    onError: (error) => {
      if (isSessionRejection(error)) {
        onSessionRejected(error.code);
      }
    },
  });
}
