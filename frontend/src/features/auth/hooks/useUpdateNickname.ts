import { useMutation } from '@tanstack/react-query';
import { updateNickname } from '../api';
import { applyUser } from '../session';

/** 닉네임을 설정·변경하고, 성공하면 로그인 사용자 정보를 갱신한다 */
export function useUpdateNickname() {
  return useMutation({
    mutationFn: (nickname: string) => updateNickname({ nickname }),
    onSuccess: applyUser,
  });
}
