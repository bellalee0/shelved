import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { useHints } from '../hooks/useHints';

function Entry({ label, question, answer }: { label: string; question: string; answer: string }) {
  return (
    <div className="flex flex-col gap-1 text-sm">
      <p className="font-bold break-words whitespace-pre-wrap">
        {label}: {question}
      </p>
      <p className="leading-relaxed break-words whitespace-pre-wrap">{answer}</p>
    </div>
  );
}

/** 힌트·보충 질문 기록 (읽기 전용 수사 기록). 새 질문·재전송은 없다 */
export function HintRecords({ roomId }: { roomId: number }) {
  const query = useHints(roomId);

  if (query.isError) {
    return <ErrorMessage message={query.error.message} onRetry={() => void query.refetch()} />;
  }
  if (!query.data) {
    return <LoadingIndicator label="힌트 기록을 불러오는 중…" />;
  }
  if (query.data.hints.length === 0) {
    return <p className="text-sm text-muted">아직 기록이 없습니다.</p>;
  }
  return (
    <ol aria-label="힌트 기록" className="flex flex-col gap-4">
      {query.data.hints.map((hint) => (
        <li
          key={hint.hintId}
          className="flex flex-col gap-3 rounded-md border border-line px-4 py-3"
        >
          <Entry label="질문" question={hint.question} answer={hint.content} />
          {hint.followup && (
            <div className="border-l-2 border-accent/40 pl-3">
              <Entry
                label="보충 질문"
                question={hint.followup.question}
                answer={hint.followup.content}
              />
            </div>
          )}
        </li>
      ))}
    </ol>
  );
}
