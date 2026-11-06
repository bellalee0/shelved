import type * as Phaser from 'phaser';
import { frameIndexOf, type Direction, type SpriteEntry } from '../assets';
import { PLAYER_SPEED_PX_PER_SEC } from '../constants';
import { applyCharacterBody, ensureWalkAnimations, walkAnimationKey } from './character';
import { facingOf, velocityOf, type MoveInput } from './movement';

/** 플레이어 캐릭터. 키 입력 판단은 Scene이 하고, 여기서는 받은 입력대로 움직이기만 한다 */
export class Player {
  readonly sprite: Phaser.Physics.Arcade.Sprite;
  private readonly entry: SpriteEntry;
  private readonly textureKey: string;
  private facing: Direction = 'down';

  constructor(scene: Phaser.Scene, textureKey: string, entry: SpriteEntry, x: number, y: number) {
    this.entry = entry;
    this.textureKey = textureKey;
    ensureWalkAnimations(scene, textureKey, entry.animations.walk);
    this.sprite = scene.physics.add.sprite(x, y, textureKey, this.idleFrame());
    applyCharacterBody(this.sprite);
    this.sprite.setCollideWorldBounds(true);
    this.sprite.setDepth(y);
  }

  move(input: MoveInput) {
    const { vx, vy } = velocityOf(input, PLAYER_SPEED_PX_PER_SEC);
    this.sprite.setVelocity(vx, vy);
    if (vx === 0 && vy === 0) {
      this.showIdle();
    } else {
      this.facing = facingOf(vx, vy, this.facing);
      this.sprite.anims.play(walkAnimationKey(this.textureKey, this.facing), true);
    }
    this.sprite.setDepth(this.sprite.y);
  }

  stop() {
    this.sprite.setVelocity(0, 0);
    this.showIdle();
  }

  /** 시작점 배치. 아래를 보고 멈춘 상태로 둔다 */
  placeAt(x: number, y: number) {
    this.facing = 'down';
    // reset은 물리 몸체 위치까지 함께 옮기고 속도를 0으로 만든다
    (this.sprite.body as Phaser.Physics.Arcade.Body).reset(x, y);
    this.showIdle();
    this.sprite.setDepth(this.sprite.y);
  }

  get position() {
    return { x: this.sprite.x, y: this.sprite.y };
  }

  private idleFrame() {
    const walk = this.entry.animations.walk;
    return frameIndexOf(walk, this.facing, walk.idleFrame);
  }

  private showIdle() {
    this.sprite.anims.stop();
    this.sprite.setFrame(this.idleFrame());
  }
}
