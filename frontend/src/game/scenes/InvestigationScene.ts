import * as Phaser from 'phaser';
import { MAP_CACHE_KEY, PLAYER_TEXTURE_KEY, tilesetTextureKey } from '../assets';
import { emitFromScene, listenFromScene } from '../bridge';
import {
  DEPTH_ABOVE,
  DEPTH_DECOR,
  DEPTH_GROUND,
  DEPTH_PROMPT,
  DEPTH_WALLS,
  PROMPT_OFFSET_EVIDENCE_PX,
  PROMPT_OFFSET_SUSPECT_PX,
} from '../constants';
import { GameEvents } from '../events';
import { findNearestTarget, listInteractionTargets, type InteractionTarget } from '../interaction';
import { shouldIgnoreGameKeys } from '../keyboardFocus';
import { createEvidenceMarker } from '../objects/EvidenceMarker';
import { NO_MOVE_INPUT, type MoveInput } from '../objects/movement';
import { createNpc, isPlaced } from '../objects/Npc';
import { Player } from '../objects/Player';
import { reportLoadFailure } from './loadFailure';
import { getManifest, getMapData, SceneKeys } from './sceneKeys';

/** 그리는 타일 레이어와 깊이. above만 캐릭터 위에 그린다. 충돌 레이어는 그리지 않는다 */
const VISIBLE_LAYERS = [
  { name: 'ground', depth: DEPTH_GROUND },
  { name: 'walls', depth: DEPTH_WALLS },
  { name: 'decor', depth: DEPTH_DECOR },
  { name: 'above', depth: DEPTH_ABOVE },
] as const;

const PROMPT_LABEL: Record<InteractionTarget['type'], string> = {
  SUSPECT: 'E: 대화',
  EVIDENCE: 'E: 조사',
};

type MoveKeys = Record<
  'w' | 'a' | 's' | 'd' | 'up' | 'down' | 'left' | 'right',
  Phaser.Input.Keyboard.Key
>;

/**
 * 수사 Scene. 맵·캐릭터·물증을 그리고 이동·충돌·상호작용 거리 판정을 한다.
 * 입력은 막힌 상태로 시작하며 React의 SET_INPUT_ENABLED로만 바뀐다.
 */
export class InvestigationScene extends Phaser.Scene {
  /** 맵 위 캐릭터. MVP에서는 길이 1 (frontend.md "맵 위 캐릭터는 배열로") */
  private players: Player[] = [];
  private targets: InteractionTarget[] = [];
  private inputEnabled = false;
  private moveKeys!: MoveKeys;
  private prompt!: Phaser.GameObjects.Text;

  constructor() {
    super(SceneKeys.INVESTIGATION);
  }

  create() {
    const mapData = getMapData(this);
    const manifest = getManifest(this);
    // 같은 Scene이 다시 시작될 때를 대비해 필드를 초기화한다
    this.players = [];
    this.inputEnabled = false;

    const map = this.make.tilemap({ key: MAP_CACHE_KEY });
    const tilesets = map.tilesets.flatMap((tileset) => {
      const added = map.addTilesetImage(tileset.name, tilesetTextureKey(tileset.name));
      return added ? [added] : [];
    });

    const collision = map.createLayer(mapData.collisionLayer, tilesets);
    if (!collision) {
      reportLoadFailure(this, `충돌 레이어 없음: ${mapData.collisionLayer}`);
      return;
    }
    collision.setVisible(false);
    collision.setCollisionByExclusion([-1]);

    for (const layer of VISIBLE_LAYERS) {
      if (layer.name !== mapData.collisionLayer && map.getLayer(layer.name)) {
        map.createLayer(layer.name, tilesets)?.setDepth(layer.depth);
      }
    }

    this.physics.world.setBounds(0, 0, map.widthInPixels, map.heightInPixels);

    for (const evidence of mapData.evidences) {
      createEvidenceMarker(this, evidence);
    }
    const npcSprites = mapData.suspects
      .filter(isPlaced)
      .map((suspect) => createNpc(this, suspect, manifest));
    this.targets = listInteractionTargets(mapData.suspects, mapData.evidences);

    // 최초 배치는 Scene이 직접 한다 (React가 MAP_READY 전에 보낸 PLACE_AT_SPAWN은 받을 수 없으므로)
    this.players.push(
      new Player(this, PLAYER_TEXTURE_KEY, manifest.player, mapData.spawnX, mapData.spawnY),
    );
    for (const player of this.players) {
      this.physics.add.collider(player.sprite, collision);
      this.physics.add.collider(player.sprite, npcSprites);
    }

    const camera = this.cameras.main;
    camera.setBounds(0, 0, map.widthInPixels, map.heightInPixels);
    camera.startFollow(this.players[0].sprite, true);

    this.prompt = this.add
      .text(0, 0, '', {
        fontFamily: 'sans-serif',
        fontSize: '14px',
        color: '#ffffff',
        backgroundColor: '#000000aa',
        padding: { x: 6, y: 3 },
      })
      .setOrigin(0.5, 1)
      .setDepth(DEPTH_PROMPT)
      .setResolution(2)
      .setVisible(false);

    this.setUpKeyboard();

    listenFromScene(this, GameEvents.SET_INPUT_ENABLED, ({ enabled }) => {
      this.inputEnabled = enabled;
      if (!enabled) {
        this.haltInput();
      }
    });
    listenFromScene(this, GameEvents.PLACE_AT_SPAWN, () => {
      for (const player of this.players) {
        player.placeAt(mapData.spawnX, mapData.spawnY);
      }
    });

    emitFromScene(this, GameEvents.MAP_READY);
  }

  update() {
    if (this.players.length === 0) {
      return;
    }
    if (!this.canReadKeys()) {
      this.haltInput();
      return;
    }
    for (const player of this.players) {
      player.move(this.readMoveInput());
    }
    this.showPrompt(this.findTarget());
  }

  private setUpKeyboard() {
    const keyboard = this.input.keyboard!;
    // 키 가로채기(preventDefault)를 하지 않는다. 가로채면 노트·대화 입력창에 WASD·E가 입력되지 않는다
    keyboard.clearCaptures();
    const { KeyCodes } = Phaser.Input.Keyboard;
    this.moveKeys = keyboard.addKeys(
      {
        w: KeyCodes.W,
        a: KeyCodes.A,
        s: KeyCodes.S,
        d: KeyCodes.D,
        up: KeyCodes.UP,
        down: KeyCodes.DOWN,
        left: KeyCodes.LEFT,
        right: KeyCodes.RIGHT,
      },
      false,
    ) as MoveKeys;
    const interactKey = keyboard.addKey(KeyCodes.E, false);
    interactKey.on(Phaser.Input.Keyboard.Events.DOWN, this.handleInteractKey, this);
  }

  // 화면의 안내는 프레임마다 갱신되므로, 키를 누른 순간의 위치로 대상을 다시 계산한다
  private handleInteractKey() {
    const target = this.canReadKeys() ? this.findTarget() : null;
    if (target) {
      emitFromScene(this, GameEvents.INTERACT_REQUESTED, {
        type: target.type,
        targetId: target.targetId,
      });
    }
  }

  private findTarget() {
    const [player] = this.players;
    if (!player) {
      return null;
    }
    return findNearestTarget(player.position, this.targets, getMapData(this).interactionRadiusPx);
  }

  /** React가 입력을 허용했고 글자 입력 중이 아닐 때만 키를 읽는다 */
  private canReadKeys() {
    return this.inputEnabled && !shouldIgnoreGameKeys();
  }

  private readMoveInput(): MoveInput {
    const keys = this.moveKeys;
    return {
      up: keys.w.isDown || keys.up.isDown,
      down: keys.s.isDown || keys.down.isDown,
      left: keys.a.isDown || keys.left.isDown,
      right: keys.d.isDown || keys.right.isDown,
    };
  }

  /** 이동·안내·E 입력을 모두 멈춘다 */
  private haltInput() {
    for (const player of this.players) {
      player.move(NO_MOVE_INPUT);
    }
    this.showPrompt(null);
  }

  private showPrompt(target: InteractionTarget | null) {
    if (target === null) {
      this.prompt.setVisible(false);
      return;
    }
    const offset = target.type === 'SUSPECT' ? PROMPT_OFFSET_SUSPECT_PX : PROMPT_OFFSET_EVIDENCE_PX;
    this.prompt
      .setText(PROMPT_LABEL[target.type])
      .setPosition(target.x, target.y - offset)
      .setVisible(true);
  }
}
