import { useQueryClient } from '@tanstack/react-query';
import { useEffect } from 'react';
import {
  connectAuthToApiClient,
  restoreSession,
  startAuthStorageSync,
  subscribeAuthCleared,
} from '../session';
import { useAuthStore } from '../store';

/**
 * 앱 시작 시 인증 초기화를 한 곳에서 한다. app의 AuthBootstrap 컴포넌트만 사용한다.
 * - 공통 axios 인스턴스에 토큰 getter·재발급 함수 등록
 * - 다른 탭 로그아웃 감지(storage 이벤트)
 * - 로그인 상태가 지워지면 Query 캐시 전체 정리
 * - 저장된 리프레시 토큰으로 로그인 복원
 */
export function useAuthBootstrap() {
  const queryClient = useQueryClient();
  const restoreStatus = useAuthStore((state) => state.restoreStatus);
  const restoreErrorMessage = useAuthStore((state) => state.restoreErrorMessage);

  useEffect(() => {
    connectAuthToApiClient();
    const stopStorageSync = startAuthStorageSync();
    const unsubscribe = subscribeAuthCleared(() => {
      // 진행 중인 조회를 먼저 취소해 늦게 도착한 이전 사용자 응답이 캐시에 다시 들어가지 않게 한다
      void queryClient.cancelQueries();
      queryClient.clear();
    });
    if (useAuthStore.getState().restoreStatus === 'restoring') {
      void restoreSession();
    }
    return () => {
      stopStorageSync();
      unsubscribe();
    };
  }, [queryClient]);

  return { restoreStatus, restoreErrorMessage, retryRestore: restoreSession };
}
