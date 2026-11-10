import { DIFFICULTY_LABELS } from '../../../shared/constants/labels';
import { formatTimeLimit } from '../../../shared/utils/formatTime';
import type { RoomBriefingResponse } from '../types';
import { BriefingSuspects } from './BriefingSuspects';

/** 사전 정보 본문. 수사 준비 화면과 사전 정보 패널이 함께 쓴다 */
export function BriefingContent({ briefing }: { briefing: RoomBriefingResponse }) {
  return (
    <div className="flex flex-col gap-4 text-sm leading-relaxed">
      <dl className="flex flex-wrap gap-x-4 gap-y-1 text-muted">
        <div className="flex gap-1">
          <dt>난이도</dt>
          <dd className="text-ink">{DIFFICULTY_LABELS[briefing.difficulty]}</dd>
        </div>
        <div className="flex gap-1">
          <dt>제한시간</dt>
          <dd className="text-ink">{formatTimeLimit(briefing.timeLimitSec)}</dd>
        </div>
        <div className="flex gap-1">
          <dt>힌트</dt>
          <dd className="text-ink">{briefing.hintLimit}회</dd>
        </div>
        <div className="flex gap-1">
          <dt>지목</dt>
          <dd className="text-ink">{briefing.accusationLimit}회</dd>
        </div>
      </dl>
      <BriefingSection title="사건 소개" body={briefing.introduction} />
      <BriefingSection title="수사 목표" body={briefing.investigationGoal} />
      <BriefingSection title="배경지식" body={briefing.backgroundKnowledge} />
      <BriefingSuspects suspects={briefing.suspects} />
    </div>
  );
}

function BriefingSection({ title, body }: { title: string; body: string }) {
  return (
    <section className="flex flex-col gap-1">
      <h3 className="font-bold">{title}</h3>
      <p className="whitespace-pre-line">{body}</p>
    </section>
  );
}
