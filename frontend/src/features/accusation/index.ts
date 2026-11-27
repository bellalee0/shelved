// features/accusation의 공개 인터페이스. 게임 화면(과 FE-12의 채점 대기·결과 화면)은 이 파일만 가져다 쓴다.
export { AccusationPanel } from './components/AccusationPanel';
export { AccusationRecordList } from './components/AccusationRecordList';
export type { AccusationSuspect } from './components/AccusationRecordList';
export { GradingRetry, GRADING_FAILED_MESSAGE } from './components/GradingRetry';
export { useAccusation, type AccusationController } from './hooks/useAccusation';
export { gradingRetryBlockReason, secondsUntilRetry } from './accusations';
