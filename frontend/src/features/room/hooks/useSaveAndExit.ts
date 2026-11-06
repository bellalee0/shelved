import { useCallback, useRef, useState } from 'react';
import { pauseRoom } from '../api';
import { isEndedStatus } from '../sessionPhase';

/** 노트 저장 결과. features/note의 NoteFlushResult와 같은 값이다 (features 간 내부 파일을 가져오지 않으려고 따로 적는다) */
export type NoteFlushOutcome = 'SAVED' | 'NO_CHANGES' | 'FAILED';

/**
 * 저장 후 나가기 단계.
 * - idle: 시작 전 / flushing: 노트 저장 중 / flushFailed: 노트 저장 실패 (다시 시도 · 그래도 나가기)
 * - pausing: 일시정지 요청 중 / pauseFailed: 일시정지 실패 (다시 시도)
 */
export type ExitStep = 'idle' | 'flushing' | 'flushFailed' | 'pausing' | 'pauseFailed';

type UseSaveAndExitParams = {
  roomId: number;
  /** 저장 안 된 노트를 저장한다 */
  flushNote: () => Promise<NoteFlushOutcome>;
  /** pause가 PAUSED(또는 종료)로 확인된 뒤 부른다. 하트비트 중지·연결 삭제·메인 이동을 맡는다 */
  onLeave: () => void;
};

/**
 * "저장 후 나가기": 노트 저장 확인 → pause → PAUSED 확인 → 떠나기 (docs/api/room.md).
 * 각 단계가 실패하면 그 단계에서 멈추고 다시 시도할 수 있게 한다.
 */
export function useSaveAndExit({ roomId, flushNote, onLeave }: UseSaveAndExitParams) {
  const [step, setStep] = useState<ExitStep>('idle');
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const busyRef = useRef(false);

  const pause = useCallback(async () => {
    setStep('pausing');
    setErrorMessage(null);
    try {
      const timer = await pauseRoom(roomId);
      if (timer.status === 'PAUSED' || isEndedStatus(timer.status)) {
        setStep('idle');
        onLeave();
        return;
      }
      setErrorMessage('수사를 일시정지하지 못했습니다. 다시 시도해 주세요.');
    } catch (error) {
      setErrorMessage(error instanceof Error ? error.message : String(error));
    }
    setStep('pauseFailed');
  }, [roomId, onLeave]);

  const run = useCallback(async (task: () => Promise<void>) => {
    if (busyRef.current) {
      return;
    }
    busyRef.current = true;
    try {
      await task();
    } finally {
      busyRef.current = false;
    }
  }, []);

  /** 노트 저장부터 시작한다 (처음 누를 때, 노트 저장 다시 시도) */
  const start = useCallback(
    () =>
      run(async () => {
        setStep('flushing');
        setErrorMessage(null);
        const result = await flushNote();
        if (result === 'FAILED') {
          setStep('flushFailed');
          return;
        }
        await pause();
      }),
    [run, flushNote, pause],
  );

  /** 노트 저장 실패를 무시하고 나간다. 초안은 이 기기(localStorage)에 남는다 */
  const leaveWithoutNote = useCallback(() => run(pause), [run, pause]);

  /** pause 다시 시도 */
  const retryPause = useCallback(() => run(pause), [run, pause]);

  const cancel = useCallback(() => {
    if (!busyRef.current) {
      setStep('idle');
      setErrorMessage(null);
    }
  }, []);

  return { step, errorMessage, start, leaveWithoutNote, retryPause, cancel };
}
