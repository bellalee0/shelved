import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { useNoteRecord } from '../hooks/useNoteRecord';

type NoteRecordsProps = {
  roomId: number;
  userId: number;
  /** 아직 끝나지 않은 수사면 이어서 진행해 초안을 저장할 수 있다고 안내한다 */
  canSaveLater: boolean;
};

/** 서버에 저장된 개인 노트와 이 기기의 미저장 초안 (읽기 전용 수사 기록) */
export function NoteRecords({ roomId, userId, canSaveLater }: NoteRecordsProps) {
  const { query, unsavedDraft } = useNoteRecord(roomId, userId);

  let saved;
  if (query.isError) {
    saved = <ErrorMessage message={query.error.message} onRetry={() => void query.refetch()} />;
  } else if (!query.data) {
    saved = <LoadingIndicator label="노트를 불러오는 중…" />;
  } else if (query.data.content === '') {
    saved = <p className="text-sm text-muted">아직 기록이 없습니다.</p>;
  } else {
    saved = (
      <>
        <p className="rounded-md border border-line bg-paper px-3 py-2 text-sm break-words whitespace-pre-wrap">
          {query.data.content}
        </p>
        <p className="text-xs text-muted">저장된 내용 · 편집 불가</p>
      </>
    );
  }

  return (
    <>
      <section aria-labelledby="record-note-title" className="flex flex-col gap-2">
        <h2 id="record-note-title" className="text-lg font-bold">
          서버에 저장된 개인 노트
        </h2>
        {saved}
      </section>
      {/* 서버 노트를 받기 전에는 같은 내용인지 알 수 없으므로 초안을 판단하지 않는다 */}
      {!query.isPending && unsavedDraft && (
        <section aria-labelledby="record-draft-title" className="flex flex-col gap-2">
          <h2 id="record-draft-title" className="text-lg font-bold">
            이 기기의 미저장 초안
          </h2>
          <textarea
            readOnly
            aria-label="복사 가능한 미저장 노트"
            rows={8}
            value={unsavedDraft.content}
            className="rounded-md border border-line bg-paper px-3 py-2 text-sm"
          />
          <p className="text-xs text-muted">
            텍스트를 선택해 복사할 수 있습니다. 다른 기기로 동기화되지 않습니다.
            {canSaveLater && ' 수사를 이어서 진행한 뒤 확인하여 저장하세요.'}
          </p>
        </section>
      )}
    </>
  );
}
