import { useId, type ReactNode } from 'react';

type SidePanelProps = {
  title: string;
  onClose: () => void;
  children: ReactNode;
};

/**
 * 게임 화면 오른쪽 패널의 틀(제목·닫기 버튼). 물증·NPC·사전 정보 패널이 함께 쓴다.
 * 열고 닫는 일과 Esc는 게임 화면의 창 관리가 맡는다.
 */
export function SidePanel({ title, onClose, children }: SidePanelProps) {
  const titleId = useId();
  return (
    <section
      aria-labelledby={titleId}
      className="flex h-full w-full flex-col gap-4 overflow-y-auto border-l border-line bg-surface p-5"
    >
      <div className="flex items-start justify-between gap-4">
        <h2 id={titleId} className="text-lg font-bold">
          {title}
        </h2>
        <button
          type="button"
          aria-label="패널 닫기"
          className="text-muted hover:text-ink"
          onClick={onClose}
        >
          ✕
        </button>
      </div>
      {children}
    </section>
  );
}
