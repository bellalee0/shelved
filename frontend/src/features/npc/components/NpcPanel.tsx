import { SidePanel } from '../../../shared/components/SidePanel';

type NpcPanelProps = {
  /** 맵 정보(suspects)의 표시 이름 */
  suspectName: string;
  onClose: () => void;
};

/** NPC 패널. 대화 기능(NPC 질문 API)은 아직 없으므로 이름과 안내만 보여 준다 */
export function NpcPanel({ suspectName, onClose }: NpcPanelProps) {
  return (
    <SidePanel title={suspectName} onClose={onClose}>
      <p className="text-muted">대화 기능은 준비 중입니다.</p>
    </SidePanel>
  );
}
