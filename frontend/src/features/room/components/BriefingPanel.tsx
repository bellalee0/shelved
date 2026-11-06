import { SidePanel } from '../../../shared/components/SidePanel';
import type { RoomBriefingResponse } from '../types';
import { BriefingContent } from './BriefingContent';

type BriefingPanelProps = {
  briefing: RoomBriefingResponse;
  onClose: () => void;
};

/** 게임 중 다시 읽는 사전 정보. 열어 둔 동안에도 타이머는 흐른다 (일시정지하지 않음) */
export function BriefingPanel({ briefing, onClose }: BriefingPanelProps) {
  return (
    <SidePanel title="사전 정보" onClose={onClose}>
      <BriefingContent briefing={briefing} />
    </SidePanel>
  );
}
