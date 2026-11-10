import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import type { AssetManifest, SpriteEntry } from '../../../game/assets';
import type { BriefingSuspect } from '../types';
import { BriefingSuspectList } from './BriefingSuspects';

const entry: SpriteEntry = {
  path: '/assets/sprites/npc-test.png',
  frameWidth: 64,
  frameHeight: 64,
  animations: {
    walk: {
      frameCount: 9,
      frameRate: 8,
      idleFrame: 0,
      rows: { up: 0, left: 1, down: 2, right: 3 },
    },
  },
};
const manifest: AssetManifest = {
  version: 1,
  player: entry,
  sprites: { 'npc-test': entry },
  maps: {},
};

describe('BriefingSuspectList', () => {
  it('얼굴·이름·소개를 인물마다 보여 준다', () => {
    // given
    const suspects: BriefingSuspect[] = [
      { suspectId: 1, name: '해리스', spriteKey: 'npc-test', publicIntro: '오래된 집사.' },
    ];

    // when
    render(<BriefingSuspectList suspects={suspects} manifest={manifest} />);

    // then
    expect(screen.getByRole('heading', { name: '등장인물' })).toBeInTheDocument();
    expect(screen.getByRole('img', { name: '해리스 얼굴' })).toBeInTheDocument();
    expect(screen.getByText('해리스')).toBeInTheDocument();
    expect(screen.getByText('오래된 집사.')).toBeInTheDocument();
  });

  it('소개가 없으면 이름만 보여 준다', () => {
    // given
    const suspects: BriefingSuspect[] = [
      { suspectId: 1, name: '해리스', spriteKey: 'npc-test', publicIntro: null },
    ];

    // when
    render(<BriefingSuspectList suspects={suspects} manifest={manifest} />);

    // then
    expect(screen.getByText('해리스')).toBeInTheDocument();
    expect(screen.getByRole('listitem').textContent).toBe('해리스');
  });

  it('spriteKey가 null이면 이름 첫 글자를 넣은 원으로 대신한다', () => {
    // given
    const suspects: BriefingSuspect[] = [
      { suspectId: 4, name: '엘리스', spriteKey: null, publicIntro: null },
    ];

    // when
    render(<BriefingSuspectList suspects={suspects} manifest={manifest} />);

    // then
    expect(screen.queryByTestId('suspect-face')).not.toBeInTheDocument();
    expect(screen.getByTestId('suspect-face-fallback')).toHaveTextContent('엘');
  });

  it('manifest에 없는 spriteKey나 manifest를 못 불러온 경우에도 대체 얼굴을 보여 준다', () => {
    // given
    const suspects: BriefingSuspect[] = [
      { suspectId: 1, name: '해리스', spriteKey: 'npc-unknown', publicIntro: null },
    ];

    // when
    render(<BriefingSuspectList suspects={suspects} manifest={null} />);

    // then
    expect(screen.getByTestId('suspect-face-fallback')).toHaveTextContent('해');
  });

  it('인물이 없으면 절 자체를 그리지 않는다', () => {
    // given, when
    render(<BriefingSuspectList suspects={[]} manifest={manifest} />);

    // then
    expect(screen.queryByRole('heading', { name: '등장인물' })).not.toBeInTheDocument();
  });
});
