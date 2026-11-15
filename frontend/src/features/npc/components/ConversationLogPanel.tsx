import { useState } from 'react';
import { Button } from '../../../shared/components/Button';
import { ErrorMessage } from '../../../shared/components/ErrorMessage';
import { LoadingIndicator } from '../../../shared/components/LoadingIndicator';
import { SidePanel } from '../../../shared/components/SidePanel';
import { formatServerDateTime } from '../../../shared/utils/formatTime';
import { useNpcMessages } from '../hooks/useNpcMessages';
import type { ConversationListItem } from '../messages';
import { NpcMessageList } from './NpcMessageList';

type ConversationLogPanelProps = {
  roomId: number;
  /** 대화한 NPC 목록 (세션 상태의 conversations + 맵 이름, joinConversations로 만든다) */
  conversations: ConversationListItem[];
  onClose: () => void;
};

/**
 * 대화 기록 패널. 대화한 NPC 목록을 보여 주고, 고른 NPC의 대화 원문을 읽기 전용으로 보여 준다.
 * 진위·중요도 표시나 요약은 하지 않으며, 노트로 자동 입력하지 않는다.
 * 원문은 대화창과 같은 쿼리 캐시(useNpcMessages)에서 읽으므로 두 화면의 내용이 어긋나지 않는다.
 */
export function ConversationLogPanel({
  roomId,
  conversations,
  onClose,
}: ConversationLogPanelProps) {
  const [selectedId, setSelectedId] = useState<number | null>(null);
  const selected = conversations.find((item) => item.suspectId === selectedId) ?? null;

  if (selected) {
    return (
      <SidePanel title={`대화 기록 · ${selected.name}`} onClose={onClose}>
        <Button variant="ghost" className="self-start" onClick={() => setSelectedId(null)}>
          ← 목록으로
        </Button>
        <ConversationDetail roomId={roomId} item={selected} />
      </SidePanel>
    );
  }

  return (
    <SidePanel title="대화 기록" onClose={onClose}>
      {conversations.length === 0 ? (
        <p className="text-sm text-muted">아직 대화한 인물이 없습니다.</p>
      ) : (
        <ul className="flex flex-col gap-2">
          {conversations.map((item) => (
            <li key={item.suspectId}>
              <button
                type="button"
                className="flex w-full items-center justify-between gap-3 rounded-md border border-line bg-surface px-4 py-3 text-left hover:border-accent"
                onClick={() => setSelectedId(item.suspectId)}
              >
                <span className="font-medium">{item.name}</span>
                <span className="text-xs text-muted">
                  메시지 {item.messageCount}개 · {formatServerDateTime(item.lastMessageAt)}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </SidePanel>
  );
}

function ConversationDetail({ roomId, item }: { roomId: number; item: ConversationListItem }) {
  const query = useNpcMessages(roomId, item.suspectId);

  if (query.isPending) {
    return <LoadingIndicator label="대화 기록을 불러오는 중…" />;
  }
  if (query.isError) {
    return <ErrorMessage message={query.error.message} onRetry={() => void query.refetch()} />;
  }
  if (query.data.messages.length === 0) {
    return <p className="text-sm text-muted">기록된 대화가 없습니다.</p>;
  }
  return <NpcMessageList messages={query.data.messages} suspectName={item.name} />;
}
