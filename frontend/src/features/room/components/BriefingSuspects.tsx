import type { AssetManifest } from '../../../game/assets';
import { findFaceEntry, nameInitial, spriteFaceStyle } from '../briefingFace';
import { useAssetManifest } from '../hooks/useAssetManifest';
import type { BriefingSuspect } from '../types';

const FACE_SIZE_PX = 40;

/** 사전 정보의 "등장인물" 절. 얼굴 그림을 위해 manifest를 읽는다 */
export function BriefingSuspects({ suspects }: { suspects: BriefingSuspect[] }) {
  const needsManifest = suspects.some((suspect) => suspect.spriteKey !== null);
  const { data: manifest } = useAssetManifest(needsManifest);
  return <BriefingSuspectList suspects={suspects} manifest={manifest ?? null} />;
}

type BriefingSuspectListProps = {
  suspects: BriefingSuspect[];
  manifest: AssetManifest | null;
};

export function BriefingSuspectList({ suspects, manifest }: BriefingSuspectListProps) {
  if (suspects.length === 0) {
    return null;
  }
  return (
    <section className="flex flex-col gap-2">
      <h3 className="font-bold">등장인물</h3>
      <ul className="flex flex-col gap-2">
        {suspects.map((suspect) => (
          <li key={suspect.suspectId} className="flex items-center gap-3">
            <SuspectFace suspect={suspect} manifest={manifest} />
            <div className="flex min-w-0 flex-col">
              <span className="font-bold">{suspect.name}</span>
              {suspect.publicIntro && <span className="text-muted">{suspect.publicIntro}</span>}
            </div>
          </li>
        ))}
      </ul>
    </section>
  );
}

/** 얼굴. 스프라이트가 없으면 이름 첫 글자를 넣은 원으로 대신한다. 이름은 옆 글자로 읽히므로 그림은 가린다 */
function SuspectFace({
  suspect,
  manifest,
}: {
  suspect: BriefingSuspect;
  manifest: AssetManifest | null;
}) {
  const entry = findFaceEntry(manifest, suspect.spriteKey);
  if (entry) {
    return (
      <div
        role="img"
        aria-label={`${suspect.name} 얼굴`}
        data-testid="suspect-face"
        className="shrink-0 rounded-full border border-line bg-surface"
        style={spriteFaceStyle(entry, FACE_SIZE_PX)}
      />
    );
  }
  return (
    <div
      aria-hidden="true"
      data-testid="suspect-face-fallback"
      className="flex shrink-0 items-center justify-center rounded-full bg-line font-bold"
      style={{ width: FACE_SIZE_PX, height: FACE_SIZE_PX }}
    >
      {nameInitial(suspect.name)}
    </div>
  );
}
