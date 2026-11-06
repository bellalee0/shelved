import { useEffect, useState } from 'react';
import { computeDisplayRemainingMs, type TimerSnapshot } from '../timer';

/** 화면 타이머를 다시 그리는 간격 */
const TICK_INTERVAL_MS = 1000;

/**
 * 서버 타이머 기준점으로 1초마다 줄어드는 남은 시간. 기준점(snapshot)이 바뀌면 그 값으로 다시 맞춘다.
 * 진행 중이 아니면 서버 값을 그대로 돌려준다. 기준점이 없으면 null.
 */
export function useDisplayRemainingMs(snapshot: TimerSnapshot | null): number | null {
  const [nowMs, setNowMs] = useState(() => Date.now());
  const isRunning = snapshot?.timer.status === 'IN_PROGRESS';

  useEffect(() => {
    if (!isRunning) {
      return;
    }
    const id = setInterval(() => setNowMs(Date.now()), TICK_INTERVAL_MS);
    return () => clearInterval(id);
  }, [isRunning, snapshot]);

  return snapshot ? computeDisplayRemainingMs(snapshot, nowMs) : null;
}
