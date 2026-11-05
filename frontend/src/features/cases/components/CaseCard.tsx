import { DIFFICULTY_LABELS } from '../../../shared/constants/labels';
import { Button } from '../../../shared/components/Button';
import { formatTimeLimit } from '../../../shared/utils/formatTime';
import type { CaseCardResponse } from '../types';
import { CaseCover } from './CaseCover';

type CaseCardProps = {
  caseCard: CaseCardResponse;
  /** 이 사건에 종료되지 않은 내 수사가 있으면 "진행 중" 배지를 보여준다 */
  hasActiveRoom: boolean;
  onStart: (caseId: number) => void;
};

const PILL_CLASS_NAME = 'rounded-full border border-line px-2 py-0.5 text-xs text-muted';

/** 사건 카드 한 장 (와이어프레임 ② 사건 카드) */
export function CaseCard({ caseCard, hasActiveRoom, onStart }: CaseCardProps) {
  return (
    <article className="relative flex flex-col overflow-hidden rounded-lg border border-line bg-surface">
      <CaseCover coverImageUrl={caseCard.coverImageUrl} title={caseCard.displayTitle} />
      {hasActiveRoom && (
        <span className="absolute top-2 left-2 rounded-full bg-accent px-2 py-0.5 text-xs font-medium text-surface">
          진행 중
        </span>
      )}
      <div className="flex flex-1 flex-col gap-3 p-4">
        <h3 className="text-lg font-bold">{caseCard.displayTitle}</h3>
        <ul aria-label="난이도와 태그" className="flex flex-wrap gap-1.5">
          <li className={`${PILL_CLASS_NAME} border-accent text-accent`}>
            {DIFFICULTY_LABELS[caseCard.difficulty]}
          </li>
          {caseCard.keywordTags.map((tag) => (
            <li key={tag} className={PILL_CLASS_NAME}>
              {tag}
            </li>
          ))}
        </ul>
        <p className="text-sm text-muted">
          제한시간 {formatTimeLimit(caseCard.timeLimitSec)} · 힌트 {caseCard.hintLimit}회 · 지목{' '}
          {caseCard.accusationLimit}회
        </p>
        <Button
          className="mt-auto"
          aria-label={`${caseCard.displayTitle} 수사 시작`}
          onClick={() => onStart(caseCard.caseId)}
        >
          수사 시작
        </Button>
      </div>
    </article>
  );
}
