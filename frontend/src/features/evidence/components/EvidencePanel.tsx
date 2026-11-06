import { useEffect } from 'react';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { SidePanel } from '../../../shared/components/SidePanel';
import { useInspectEvidence } from '../hooks/useInspectEvidence';

type EvidencePanelProps = {
  roomId: number;
  evidenceId: number;
  onClose: () => void;
  /** 세션 상태 때문에 조사가 거부됐을 때의 오류 코드 */
  onSessionRejected: (code: string) => void;
};

/**
 * 물증 설명 패널. 열릴 때 조사 API를 호출해 이름·설명을 보여 준다.
 * 같은 물증을 다시 조사하면 게임 화면이 key를 바꿔 패널을 새로 그린다(다시 조사).
 * 자동 수집·노트 자동 입력은 하지 않는다.
 */
export function EvidencePanel({
  roomId,
  evidenceId,
  onClose,
  onSessionRejected,
}: EvidencePanelProps) {
  const { mutate, data, error, isPending } = useInspectEvidence(roomId, onSessionRejected);

  useEffect(() => {
    mutate(evidenceId);
  }, [evidenceId, mutate]);

  return (
    <SidePanel title={data?.name ?? '물증 조사'} onClose={onClose}>
      {isPending && <LoadingIndicator label="조사하는 중…" />}
      {error && <ErrorMessage message={error.message} onRetry={() => mutate(evidenceId)} />}
      {data && <p className="whitespace-pre-line leading-relaxed">{data.description}</p>}
    </SidePanel>
  );
}
