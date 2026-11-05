import { useNavigate } from 'react-router';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { Modal } from '../../../shared/components/Modal';
import { useStartInvestigation } from '../hooks/useStartInvestigation';

type StartInvestigationDialogProps = {
  caseId: number;
  displayTitle: string;
  /** 제한시간·힌트·지목 횟수 안내 문구 */
  rulesSummary: string;
  /** 취소·닫기. 선택한 사건을 해제하는 일은 부모가 한다 */
  onClose: () => void;
};

/**
 * 수사 시작 확인 창. "수사 시작"을 누르면 세션을 만들고 게임 화면으로 이동한다.
 * 같은 사건의 진행 중인 수사가 있으면(409) 확인 창 대신 "진행 중인 수사가 있습니다" 안내를 보여주고, 이어하기로 안내한다.
 */
export function StartInvestigationDialog({
  caseId,
  displayTitle,
  rulesSummary,
  onClose,
}: StartInvestigationDialogProps) {
  const navigate = useNavigate();
  const { start, isStarting, errorMessage, existingRoomId } = useStartInvestigation();

  if (existingRoomId !== null) {
    return (
      <Modal title="진행 중인 수사가 있습니다" onClose={onClose}>
        <p className="text-sm text-muted">
          이 사건은 이미 수사 중입니다. 새로 시작하지 않고 이어서 진행합니다.
        </p>
        <div className="mt-6 flex justify-end gap-2">
          <Button variant="ghost" onClick={onClose}>
            취소
          </Button>
          <Button onClick={() => navigate(`/rooms/${existingRoomId}`)}>이어하기</Button>
        </div>
      </Modal>
    );
  }

  return (
    // 요청 중에 닫으면 화면에서 사라진 뒤 게임 화면으로 이동하게 되므로 닫지 못하게 한다
    <Modal title="수사를 시작할까요?" onClose={isStarting ? () => undefined : onClose}>
      <p className="font-medium">{displayTitle}</p>
      <p className="mt-1 text-sm text-muted">{rulesSummary}</p>
      {errorMessage && (
        <div className="mt-4">
          <ErrorMessage message={errorMessage} />
        </div>
      )}
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" disabled={isStarting} onClick={onClose}>
          취소
        </Button>
        <Button isLoading={isStarting} onClick={() => start(caseId)}>
          수사 시작
        </Button>
      </div>
    </Modal>
  );
}
