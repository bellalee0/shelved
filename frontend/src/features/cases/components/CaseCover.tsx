import { useState } from 'react';

type CaseCoverProps = {
  coverImageUrl: string | null;
  title: string;
};

/** 사건 카드의 표지. 이미지가 없거나 불러오지 못하면 기본 표시를 보여준다 */
export function CaseCover({ coverImageUrl, title }: CaseCoverProps) {
  const [hasLoadFailed, setHasLoadFailed] = useState(false);

  if (!coverImageUrl || hasLoadFailed) {
    return (
      <div
        data-testid="case-cover-fallback"
        className="flex aspect-video items-center justify-center bg-line/40 text-sm text-muted"
      >
        표지 이미지 없음
      </div>
    );
  }
  return (
    <img
      src={coverImageUrl}
      alt={`${title} 표지`}
      className="aspect-video w-full object-cover"
      onError={() => setHasLoadFailed(true)}
    />
  );
}
