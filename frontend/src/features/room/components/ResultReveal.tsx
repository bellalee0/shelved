import type { ReactNode } from 'react';
import { ROOM_STATUS_LABELS } from '../../../shared/constants/labels';
import { formatDuration, formatServerDateTime } from '../../../shared/utils/formatTime';
import type { RoomResultResponse } from '../types';
import { SuspectPortrait } from './BriefingSuspects';

type ResultRevealProps = {
  result: RoomResultResponse;
  /** 범인 얼굴. GET result에는 없어 GET map의 suspects에서 찾은 값이며, 없으면 기본 실루엣 */
  culpritSpriteKey: string | null;
  /** 플레이 기록 아래의 "내 지목 기록" 목록 (accusation 기능은 페이지가 조립한다) */
  accusationRecords: ReactNode;
};

type Headline = {
  title: string;
  description: string;
};

function headlineOf({ status, accusationCompletedCount }: RoomResultResponse): Headline {
  switch (status) {
    case 'CLEARED':
      return {
        title: '사건을 해결했습니다',
        description: `${accusationCompletedCount}번째 지목에서 범인과 트릭을 모두 맞혔습니다.`,
      };
    case 'FAILED':
      return {
        title: '지목 기회를 모두 사용했습니다',
        description: `${accusationCompletedCount}번의 지목 모두 정답에 이르지 못했습니다. 원작과 진상을 확인해 보세요.`,
      };
    default:
      return {
        title: '제한시간이 끝났습니다',
        description: '시간 안에 정답을 제출하지 못했습니다. 원작과 진상을 확인해 보세요.',
      };
  }
}

function Section({ title, children }: { title: string; children: ReactNode }) {
  return (
    <section className="flex flex-col gap-2">
      <h2 className="text-sm font-bold text-muted">{title}</h2>
      {children}
    </section>
  );
}

/** 서버가 준 글을 그대로 보여 준다. 줄바꿈은 유지하고 HTML로 해석하지 않는다 */
function PlainText({ text, className = '' }: { text: string; className?: string }) {
  return <p className={`leading-relaxed break-words whitespace-pre-wrap ${className}`}>{text}</p>;
}

/** 결과 화면의 위에서 아래로: 머리말, 원작 공개, 범인, 트릭·해설, 플레이 기록, 내 지목 기록 */
export function ResultReveal({ result, culpritSpriteKey, accusationRecords }: ResultRevealProps) {
  const headline = headlineOf(result);
  const { source, culprit } = result;

  return (
    <div className="flex flex-col gap-8">
      <header className="flex flex-col gap-2 rounded-lg border-2 border-accent bg-surface p-5">
        <p className="text-sm text-muted">
          {result.displayTitle} · {formatServerDateTime(result.endedAt)} 종료
        </p>
        <span className="self-start rounded-full border border-line px-2 py-0.5 text-xs">
          {ROOM_STATUS_LABELS[result.status]}
        </span>
        <h1 className="text-2xl font-bold">{headline.title}</h1>
        <p className="text-sm text-muted">{headline.description}</p>
      </header>

      <Section title="이 사건의 원작은">
        <p className="text-lg font-bold break-words">{source.novelTitle}</p>
        <p className="text-sm">{source.novelAuthor}</p>
        <PlainText text={source.novelIntro} className="text-sm text-muted" />
      </Section>

      <Section title="범인">
        <div className="flex items-center gap-3">
          <SuspectPortrait name={culprit.name} spriteKey={culpritSpriteKey} />
          <span className="text-lg font-bold">{culprit.name}</span>
        </div>
      </Section>

      <Section title="트릭">
        <PlainText text={result.trickSummary} className="font-bold" />
      </Section>

      <Section title="해설">
        <PlainText text={result.revealExplanation} />
      </Section>

      <Section title="플레이 기록">
        <dl className="grid grid-cols-2 gap-3 sm:grid-cols-4">
          <RecordTile label="걸린 시간" value={formatDuration(result.durationSec)} />
          <RecordTile label="힌트 사용" value={`${result.hintUsedCount}회`} />
          <RecordTile label="지목" value={`${result.accusationCompletedCount}회`} />
          <RecordTile label="종료 시각" value={formatServerDateTime(result.endedAt)} />
        </dl>
      </Section>

      <Section title="내 지목 기록">
        {result.accusations.length === 0 ? (
          <p className="text-sm text-muted">제출한 지목이 없습니다.</p>
        ) : (
          accusationRecords
        )}
      </Section>
    </div>
  );
}

function RecordTile({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-md border border-line bg-surface px-3 py-2">
      <dt className="text-xs text-muted">{label}</dt>
      <dd className="font-mono font-bold">{value}</dd>
    </div>
  );
}
