import { NAME_LABEL_OFFSET_PX } from '../constants';

/** NPC 이름 글자의 위치. 글자 아랫단 가운데가 NPC 기준점(발밑)에서 NAME_LABEL_OFFSET_PX만큼 위에 온다 */
export function nameLabelPosition(npcX: number, npcY: number) {
  return { x: npcX, y: npcY - NAME_LABEL_OFFSET_PX };
}
