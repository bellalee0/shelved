import { useEffect, useId, type ReactNode } from 'react';
import { createPortal } from 'react-dom';

type ModalProps = {
  title: string;
  /** 닫기 버튼·바깥 영역 클릭 시 호출한다. 실제로 닫을지는 부모가 정한다 */
  onClose: () => void;
  children: ReactNode;
  /**
   * true면 Esc 키로도 onClose를 호출한다. 기본값 false.
   * 게임 화면은 열린 창 목록을 한 곳에서 관리하며 Esc도 거기서 처리하므로(frontend.md 조작·창 관리 규칙) 켜지 않는다.
   */
  closeOnEscape?: boolean;
};

/** 화면 위에 띄우는 대화 상자. 렌더링되면 열린 상태이고, 닫으려면 부모가 렌더링을 멈춘다 */
export function Modal({ title, onClose, children, closeOnEscape = false }: ModalProps) {
  const titleId = useId();

  useEffect(() => {
    if (!closeOnEscape) {
      return;
    }
    function handleKeyDown(event: KeyboardEvent) {
      if (event.key === 'Escape') {
        onClose();
      }
    }
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, [closeOnEscape, onClose]);

  return createPortal(
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-ink/40 p-6"
      onClick={onClose}
    >
      <div
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        className="w-full max-w-md rounded-lg bg-surface p-6 shadow-lg"
        // 대화 상자 안쪽 클릭이 바깥 영역 클릭(onClose)으로 전달되지 않게 막는다
        onClick={(event) => event.stopPropagation()}
      >
        <div className="mb-4 flex items-start justify-between gap-4">
          <h2 id={titleId} className="text-lg font-bold">
            {title}
          </h2>
          <button
            type="button"
            aria-label="닫기"
            className="text-muted hover:text-ink"
            onClick={onClose}
          >
            ✕
          </button>
        </div>
        {children}
      </div>
    </div>,
    document.body,
  );
}
