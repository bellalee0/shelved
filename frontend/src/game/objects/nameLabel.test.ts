import { describe, expect, it } from 'vitest';
import {
  DEPTH_NAME_LABEL,
  DEPTH_PROMPT,
  NAME_LABEL_HEIGHT_PX,
  NAME_LABEL_OFFSET_PX,
  PROMPT_OFFSET_SUSPECT_PX,
} from '../constants';
import { nameLabelPosition } from './nameLabel';

describe('nameLabelPosition', () => {
  it('이름을 NPC 기준점 바로 위 가운데에 둔다', () => {
    // given
    const npc = { x: 368, y: 208 };

    // when
    const result = nameLabelPosition(npc.x, npc.y);

    // then
    expect(result).toEqual({ x: 368, y: 208 - NAME_LABEL_OFFSET_PX });
  });
});

describe('이름과 상호작용 안내의 배치', () => {
  it('이름은 안내보다 아래 깊이에 그린다', () => {
    // given, when, then
    expect(DEPTH_NAME_LABEL).toBeLessThan(DEPTH_PROMPT);
  });

  it('NPC 안내는 이름 상자 윗면보다 위에 뜬다', () => {
    // given
    const nameTopOffset = NAME_LABEL_OFFSET_PX + NAME_LABEL_HEIGHT_PX;

    // when, then
    expect(PROMPT_OFFSET_SUSPECT_PX).toBeGreaterThan(nameTopOffset);
  });
});
