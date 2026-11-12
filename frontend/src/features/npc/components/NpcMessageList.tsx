import type { ReactNode } from 'react';
import type { NpcMessageResponse } from '../types';

type NpcMessageListProps = {
  messages: NpcMessageResponse[];
  /** 말풍선 머리에 붙일 NPC 이름 */
  suspectName: string;
  /** 기록 뒤에 이어 붙일 내용 (처리 중·실패한 질문 말풍선) */
  children?: ReactNode;
};

type MessageBubbleProps = {
  senderType: NpcMessageResponse['senderType'];
  label: string;
  children: ReactNode;
  /** 아직 기록되지 않은 질문(처리 중·실패)은 흐리게 보여 준다 */
  pending?: boolean;
};

/** 말풍선 하나. 플레이어 질문은 오른쪽, NPC 답변은 왼쪽에 둔다 */
export function MessageBubble({
  senderType,
  label,
  children,
  pending = false,
}: MessageBubbleProps) {
  const isUser = senderType === 'USER';
  return (
    <li className={`flex flex-col gap-1 ${isUser ? 'items-end' : 'items-start'}`}>
      <span className="text-xs text-muted">{label}</span>
      <div
        className={`max-w-[85%] rounded-md px-3 py-2 text-sm leading-relaxed break-words whitespace-pre-wrap ${
          isUser ? 'bg-accent text-surface' : 'border border-line bg-paper text-ink'
        } ${pending ? 'opacity-70' : ''}`}
      >
        {children}
      </div>
    </li>
  );
}

/** NPC 대화 원문 목록 (sequenceNo 순). 진위·중요도 표시나 요약은 하지 않는다 */
export function NpcMessageList({ messages, suspectName, children }: NpcMessageListProps) {
  return (
    <ol aria-label={`${suspectName}와의 대화`} className="flex flex-col gap-3">
      {messages.map((message) => (
        <MessageBubble
          key={message.messageId}
          senderType={message.senderType}
          label={message.senderType === 'USER' ? '나' : suspectName}
        >
          {message.content}
        </MessageBubble>
      ))}
      {children}
    </ol>
  );
}
