import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { Modal } from '../../../shared/components/Modal';
import type { ExitStep } from '../hooks/useSaveAndExit';

type ExitDialogProps = {
  step: ExitStep;
  errorMessage: string | null;
  onRetryNote: () => void;
  onLeaveWithoutNote: () => void;
  onRetryPause: () => void;
  onCancel: () => void;
};

/** 저장 후 나가기 진행 상황. idle이면 그리지 않는다 */
export function ExitDialog({
  step,
  errorMessage,
  onRetryNote,
  onLeaveWithoutNote,
  onRetryPause,
  onCancel,
}: ExitDialogProps) {
  if (step === 'idle') {
    return null;
  }
  const isBusy = step === 'flushing' || step === 'pausing';
  return (
    <Modal title="저장 후 나가기" onClose={isBusy ? () => {} : onCancel}>
      <div className="flex flex-col gap-4">
        {step === 'flushing' && <LoadingIndicator label="노트를 저장하는 중…" />}
        {step === 'pausing' && <LoadingIndicator label="수사를 일시정지하는 중…" />}
        {step === 'flushFailed' && (
          <>
            <p role="alert" className="text-sm text-danger">
              노트를 저장하지 못했습니다.
            </p>
            <div className="flex flex-wrap gap-2">
              <Button onClick={onRetryNote}>다시 시도</Button>
              <Button variant="secondary" onClick={onLeaveWithoutNote}>
                그래도 나가기 (이 기기에 초안 보관)
              </Button>
            </div>
          </>
        )}
        {step === 'pauseFailed' && (
          <ErrorMessage message={errorMessage ?? ''} onRetry={onRetryPause} />
        )}
      </div>
    </Modal>
  );
}
