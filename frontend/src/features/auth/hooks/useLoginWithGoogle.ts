import { useMutation } from '@tanstack/react-query';
import { loginWithGoogle } from '../api';
import { completeLogin } from '../session';

/**
 * Google ID 토큰(credential)으로 로그인한다.
 * 성공하면 로그인 상태가 바뀌고, 로그인 화면이 그 변화를 보고 다음 화면(닉네임 설정 또는 원래 화면)으로 이동한다.
 */
export function useLoginWithGoogle() {
  return useMutation({
    mutationFn: async (idToken: string) => {
      const response = await loginWithGoogle({ idToken });
      await completeLogin(response);
      return response.user;
    },
  });
}
