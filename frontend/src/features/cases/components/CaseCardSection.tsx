import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import type { CaseCardResponse } from '../types';
import { CaseCard } from './CaseCard';

type CaseCardSectionProps = {
  /** 아직 불러오지 못했으면 undefined */
  caseCards: CaseCardResponse[] | undefined;
  isLoading: boolean;
  /** 조회에 실패했을 때 보여줄 문구 (백엔드 message 그대로) */
  errorMessage: string | null;
  onRetry: () => void;
  /** "진행 중" 배지를 보여줄 사건 ID들 */
  activeCaseIds: ReadonlySet<number>;
  onStart: (caseId: number) => void;
};

/** "사건" 섹션: 로딩·조회 실패(다시 시도)·빈 목록·카드 목록을 상황에 맞게 보여준다 */
export function CaseCardSection({
  caseCards,
  isLoading,
  errorMessage,
  onRetry,
  activeCaseIds,
  onStart,
}: CaseCardSectionProps) {
  let content;
  if (errorMessage) {
    content = <ErrorMessage message={errorMessage} onRetry={onRetry} />;
  } else if (isLoading || !caseCards) {
    content = <LoadingIndicator label="사건을 불러오는 중…" />;
  } else if (caseCards.length === 0) {
    content = <p className="text-muted">공개된 사건이 아직 없습니다.</p>;
  } else {
    content = (
      <div className="grid gap-4 sm:grid-cols-2">
        {caseCards.map((caseCard) => (
          <CaseCard
            key={caseCard.caseId}
            caseCard={caseCard}
            hasActiveRoom={activeCaseIds.has(caseCard.caseId)}
            onStart={onStart}
          />
        ))}
      </div>
    );
  }

  return (
    <section aria-labelledby="case-section-title" className="flex flex-col gap-3">
      <h2 id="case-section-title" className="text-xl font-bold">
        사건
      </h2>
      {content}
    </section>
  );
}
