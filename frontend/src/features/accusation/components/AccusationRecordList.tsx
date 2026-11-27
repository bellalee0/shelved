import { useState, type ReactNode } from 'react';
import type { AccusationResponse } from '../../../shared/api/types';
import { VERDICT_LABELS } from '../../../shared/constants/labels';
import { GRADING_FAILED_MESSAGE } from './GradingRetry';

/** 지목 대상 한 명 (GET map의 suspects에서 필요한 값만). 맵에 배치되지 않은 인물도 포함한다 */
export type AccusationSuspect = {
  suspectId: number;
  name: string;
  spriteKey: string | null;
};

/** 이 길이를 넘는 트릭 설명은 접어서 보여 준다 */
const COLLAPSE_LENGTH = 120;

/** 긴 글은 앞부분만 보여 주고 "펼치기"로 전부 보여 준다. 줄바꿈을 그대로 두고 HTML로 해석하지 않는다 */
function CollapsibleText({ text }: { text: string }) {
  const [expanded, setExpanded] = useState(false);
  const long = text.length > COLLAPSE_LENGTH;
  return (
    <div className="flex flex-col items-start gap-1">
      <p className="text-sm break-words whitespace-pre-wrap text-ink">
        {long && !expanded ? `${text.slice(0, COLLAPSE_LENGTH)}…` : text}
      </p>
      {long && (
        <button
          type="button"
          className="text-xs text-accent underline"
          onClick={() => setExpanded((value) => !value)}
        >
          {expanded ? '접기' : '펼치기'}
        </button>
      )}
    </div>
  );
}

type AccusationRecordItemProps = {
  suspectName: string;
  trickExplanation: string;
  attemptNo: number | null;
  /** 방금 받은 결과면 강조한다 */
  highlighted?: boolean;
  superseded?: boolean;
  /** 상태 표시 (판정·피드백, 채점 중, 채점 실패·재채점 등) */
  children: ReactNode;
};

/** 지목 기록 한 건의 틀: "N번째 지목"(완료된 것만)·대상·제출한 트릭 설명 + 상태 */
export function AccusationRecordItem({
  suspectName,
  trickExplanation,
  attemptNo,
  highlighted = false,
  superseded = false,
  children,
}: AccusationRecordItemProps) {
  return (
    <li
      className={`flex flex-col gap-2 rounded-md border px-3 py-3 ${
        highlighted ? 'border-2 border-accent bg-paper' : 'border-line'
      } ${superseded ? 'opacity-60' : ''}`}
    >
      {highlighted && <p className="text-xs font-bold text-accent">방금 받은 결과</p>}
      <p className="text-sm">
        {attemptNo !== null && <span className="mr-2 font-bold">{attemptNo}번째 지목</span>}
        <span className="text-muted">지목한 인물 </span>
        <span className="font-bold">{suspectName}</span>
      </p>
      <CollapsibleText text={trickExplanation} />
      {children}
    </li>
  );
}

/** 완료된 지목의 판정 라벨과 피드백 */
export function VerdictFeedback({ accusation }: { accusation: AccusationResponse }) {
  return (
    <div className="flex flex-col gap-1">
      {accusation.verdict && (
        <p className="text-sm">
          판정{' '}
          <span
            className={`rounded-full px-2 py-0.5 text-xs font-bold ${
              accusation.verdict === 'CORRECT' ? 'bg-accent text-surface' : 'bg-line text-ink'
            }`}
          >
            {VERDICT_LABELS[accusation.verdict]}
          </span>
        </p>
      )}
      {accusation.feedback && (
        <p className="rounded-md border border-line bg-surface px-3 py-2 text-sm leading-relaxed break-words whitespace-pre-wrap">
          {accusation.feedback}
        </p>
      )}
    </div>
  );
}

/** 이 화면이 다루고 있지 않은 지목의 상태 표시 (완료·대체됨·채점 중·실패) */
export function RecordStatus({ accusation }: { accusation: AccusationResponse }) {
  if (accusation.superseded) {
    return <p className="text-xs text-muted">새 답안으로 대체됨</p>;
  }
  switch (accusation.gradingStatus) {
    case 'COMPLETED':
      return <VerdictFeedback accusation={accusation} />;
    case 'PENDING':
      return <p className="text-xs text-muted">채점 중</p>;
    case 'ERROR':
      return <p className="text-xs text-danger">{GRADING_FAILED_MESSAGE}</p>;
  }
}

/**
 * 읽기 전용 지목 기록 목록 (결과 화면). 지목 창과 같은 항목 모양이며 재채점 버튼은 없다.
 * accusations는 서버가 준 순서(오래된 순)대로 그린다
 */
export function AccusationRecordList({ accusations }: { accusations: AccusationResponse[] }) {
  return (
    <ol aria-label="내 지목 기록" className="flex flex-col gap-3">
      {accusations.map((accusation) => (
        <AccusationRecordItem
          key={accusation.accusationId}
          suspectName={accusation.suspectName}
          trickExplanation={accusation.trickExplanation}
          attemptNo={accusation.attemptNo}
          superseded={accusation.superseded}
        >
          <RecordStatus accusation={accusation} />
        </AccusationRecordItem>
      ))}
    </ol>
  );
}
