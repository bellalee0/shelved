import { useNavigate } from 'react-router';
import { Button } from '../../../shared/components/Button';
import { Modal } from '../../../shared/components/Modal';

type ExistingRoomDialogProps = {
  /** 409 ROOM_ALREADY_EXISTS가 알려 준 기존 세션 */
  roomId: number;
  onClose: () => void;
};

/** 같은 사건의 미종료 세션이 있을 때의 안내. 새로 만들지 않고 이어하기로 보낸다 */
export function ExistingRoomDialog({ roomId, onClose }: ExistingRoomDialogProps) {
  const navigate = useNavigate();
  return (
    <Modal title="진행 중인 수사가 있습니다" onClose={onClose}>
      <p className="text-sm text-muted">
        이 사건은 이미 수사 중입니다. 새로 시작하지 않고 이어서 진행합니다.
      </p>
      <div className="mt-6 flex justify-end gap-2">
        <Button variant="ghost" onClick={onClose}>
          취소
        </Button>
        <Button onClick={() => navigate(`/rooms/${roomId}`)}>이어하기</Button>
      </div>
    </Modal>
  );
}
