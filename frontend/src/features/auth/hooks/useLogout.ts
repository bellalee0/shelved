import { useCallback, useState } from 'react';
import { logoutSession } from '../session';

/**
 * 로그아웃 실행 함수와 진행 여부.
 * 로그아웃은 Query 캐시(변경 요청 포함)를 모두 비우므로, 그 캐시에 들어가는 useMutation 대신 일반 상태로 진행 여부를 관리한다.
 */
export function useLogout() {
  const [isPending, setIsPending] = useState(false);

  const logout = useCallback(async () => {
    setIsPending(true);
    try {
      await logoutSession();
    } finally {
      setIsPending(false);
    }
  }, []);

  return { logout, isPending };
}
