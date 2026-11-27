import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { useNpcMessages } from '../hooks/useNpcMessages';
import { NpcMessageList } from './NpcMessageList';

type NpcRecordProps = {
  roomId: number;
  suspectId: number;
  /** 맵 정보의 표시 이름 */
  name: string;
};

/**
 * 한 NPC와의 대화 원문 (읽기 전용 수사 기록).
 * 처리 중·실패한 질문(pendingRequests)은 확인·재전송하지 않고 저장된 대화만 보여 준다.
 */
export function NpcRecord({ roomId, suspectId, name }: NpcRecordProps) {
  const query = useNpcMessages(roomId, suspectId);

  let content;
  if (query.isError) {
    content = <ErrorMessage message={query.error.message} onRetry={() => void query.refetch()} />;
  } else if (!query.data) {
    content = <LoadingIndicator label="대화 기록을 불러오는 중…" />;
  } else if (query.data.messages.length === 0) {
    content = <p className="text-sm text-muted">아직 기록이 없습니다.</p>;
  } else {
    content = <NpcMessageList messages={query.data.messages} suspectName={name} />;
  }

  return (
    <details open className="flex flex-col gap-3 rounded-md border border-line px-4 py-3">
      <summary className="cursor-pointer font-medium">{name}</summary>
      <div className="mt-3">{content}</div>
    </details>
  );
}
