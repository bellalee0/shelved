// Phaser 쪽 수치 상수. 플레이테스트로 조정할 수 있는 잠정값이다.

/** 맵 타일 한 칸 크기(px). 맵 제작 규칙의 32×32 */
export const TILE_SIZE = 32;

/** 게임 화면 기준 크기. 30×20 타일 맵 = 960×640이며 Scale.FIT으로 컨테이너에 맞춘다 */
export const GAME_WIDTH = 960;
export const GAME_HEIGHT = 640;

/** 플레이어 이동 속도(px/초). 대각선도 같은 속도가 되도록 정규화한다 */
export const PLAYER_SPEED_PX_PER_SEC = 140;

/**
 * 캐릭터 충돌 박스. 64×64 프레임 전체가 아니라 발밑만 잡아 한 칸(32px) 통로도 지나갈 수 있게 한다.
 * OFFSET은 프레임 좌상단 기준이다.
 */
export const CHARACTER_BODY_WIDTH = 20;
export const CHARACTER_BODY_HEIGHT = 10;
export const CHARACTER_BODY_OFFSET_X = 22;
export const CHARACTER_BODY_OFFSET_Y = 50;

/** 캐릭터의 기준점(좌표가 가리키는 곳)을 발밑 충돌 박스 가운데에 둔다. 0~1, 프레임 높이 기준 */
export const CHARACTER_ORIGIN_Y = (CHARACTER_BODY_OFFSET_Y + CHARACTER_BODY_HEIGHT / 2) / 64;

/** 그리기 순서. 캐릭터·물증은 y 좌표를 깊이로 써서 아래쪽(y가 큰) 것이 앞에 온다 */
export const DEPTH_GROUND = -30;
export const DEPTH_WALLS = -20;
export const DEPTH_DECOR = -10;
export const DEPTH_ABOVE = 100_000;
/** NPC 이름: 캐릭터(y 좌표)보다 위, "E: 대화" 안내(DEPTH_PROMPT)보다 아래 */
export const DEPTH_NAME_LABEL = 100_050;
export const DEPTH_PROMPT = 100_100;

/** NPC 이름 글자 아랫단이 NPC 기준점(발밑)에서 얼마나 위에 뜨는지(px). 머리 바로 위 */
export const NAME_LABEL_OFFSET_PX = 44;
/** 이름 글자 상자의 대략적인 높이(px). 글자 14px 이하 + 위아래 여백. 안내가 이름과 겹치지 않게 올리는 데 쓴다 */
export const NAME_LABEL_HEIGHT_PX = 18;
const NAME_LABEL_GAP_PX = 4;

/** 상호작용 안내가 대상 기준점에서 얼마나 위에 뜨는지(px). NPC는 이름 상자 위로 띄운다 */
export const PROMPT_OFFSET_SUSPECT_PX =
  NAME_LABEL_OFFSET_PX + NAME_LABEL_HEIGHT_PX + NAME_LABEL_GAP_PX;
export const PROMPT_OFFSET_EVIDENCE_PX = 18;
