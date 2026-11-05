import { useMutation } from '@tanstack/react-query';
import { useRef, useState } from 'react';
import { useNavigate } from 'react-router';
import { isApiError } from '../../../shared/api/apiError';
import { createRoom } from '../api';
import type { RoomAlreadyExistsDetail } from '../types';

function isRoomAlreadyExistsDetail(detail: unknown): detail is RoomAlreadyExistsDetail {
  return (
    typeof detail === 'object' &&
    detail !== null &&
    'roomId' in detail &&
    typeof detail.roomId === 'number'
  );
}

/**
 * 수사 시작: 세션을 만들고 성공하면 게임 화면으로 이동한다.
 * - 409 ROOM_ALREADY_EXISTS면 existingRoomId에 기존 세션 ID를 담는다 (화면이 "이어하기" 안내를 보여준다).
 * - 요청 중에는 start를 다시 불러도 두 번째 요청을 보내지 않는다 (같은 틱의 중복 클릭까지 막으려고 ref를 쓴다).
 */
export function useStartInvestigation() {
  const navigate = useNavigate();
  const isSubmittingRef = useRef(false);
  const [existingRoomId, setExistingRoomId] = useState<number | null>(null);

  const mutation = useMutation({
    mutationFn: createRoom,
    // 메인 화면으로 돌아오면 세션 목록은 마운트 시점에 다시 조회하므로 캐시를 따로 무효화하지 않는다
    onSuccess: ({ roomId }) => navigate(`/rooms/${roomId}`),
    onError: (error) => {
      if (isApiError(error) && error.code === 'ROOM_ALREADY_EXISTS') {
        if (isRoomAlreadyExistsDetail(error.detail)) {
          setExistingRoomId(error.detail.roomId);
        }
      }
    },
    onSettled: () => {
      isSubmittingRef.current = false;
    },
  });

  function start(caseId: number) {
    if (isSubmittingRef.current) {
      return;
    }
    isSubmittingRef.current = true;
    setExistingRoomId(null);
    mutation.mutate({ caseId });
  }

  return {
    start,
    isStarting: mutation.isPending,
    /** 시작 실패 시 사용자에게 보여줄 백엔드 message. 이어하기 안내 대상(409)이면 null */
    errorMessage: existingRoomId === null && mutation.error ? mutation.error.message : null,
    existingRoomId,
  };
}
