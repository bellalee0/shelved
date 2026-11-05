import { useAuthStore } from '../features/auth/store';
import { findMockUserIdByAccessToken } from './handlers/auth';
import {
  currentTimeMs,
  findMockRoom,
  listMockRooms,
  resetMockRoomDb,
  saveMockRoomDb,
  seedMockRoom,
} from './roomDb';
import {
  clearScenarios,
  getEnabledScenarios,
  SCENARIO_NAMES,
  setScenario,
  type ScenarioName,
} from './scenarios';

// 개발 중 브라우저 콘솔에서 쓰는 목 조작 도구(window.shelvedMock). 사용법은 README "목(MSW) 모드"를 본다.
// 시드·동작 함수는 "지금 로그인한 사용자"의 세션을 대상으로 한다.

type SeedKind = 'active' | 'gradingError' | 'gradingPending' | 'ended';

function currentUserId(): number {
  const userId = findMockUserIdByAccessToken(useAuthStore.getState().accessToken);
  if (userId === null) {
    throw new Error('목 모드에서 로그인한 뒤 사용하세요.');
  }
  return userId;
}

const shelvedMock = {
  /** 켤 수 있는 오류 재현 스위치 이름 목록 */
  scenarioNames: SCENARIO_NAMES,
  /** 오류 재현 스위치를 켜거나 끈다. 새로고침 후에도 유지된다 */
  setScenario(name: ScenarioName, enabled = true) {
    if (!SCENARIO_NAMES.includes(name)) {
      throw new Error(`알 수 없는 스위치입니다. 사용 가능: ${SCENARIO_NAMES.join(', ')}`);
    }
    setScenario(name, enabled);
    return getEnabledScenarios();
  },
  /** 지금 켜진 스위치 */
  scenarios: getEnabledScenarios,
  /** 모든 스위치를 끈다 */
  clearScenarios,
  /**
   * 미리 만들어 둔 세션을 내 계정에 추가한다.
   * - active: 시계탑 사건의 일시정지 세션(남은 12:48) → 카드에 "진행 중" 배지
   * - gradingError: 등대 사건의 채점 실패 세션(남은 시간 0) → "다시 채점하기"
   * - gradingPending: 등대 사건의 채점 대기 세션 → "채점 대기"
   * - ended: 시계탑 사건 해결 + 등대 사건 시간 만료 → "지난 수사"
   */
  seedRooms(kind: SeedKind = 'active') {
    const userId = currentUserId();
    if (kind === 'active') {
      seedMockRoom(userId, { caseId: 1, status: 'PAUSED', remainingMs: 768_750 });
    } else if (kind === 'gradingError') {
      seedMockRoom(userId, {
        caseId: 2,
        status: 'PAUSED',
        remainingMs: 0,
        pendingGradingStatus: 'ERROR',
      });
    } else if (kind === 'gradingPending') {
      seedMockRoom(userId, {
        caseId: 2,
        status: 'PAUSED',
        remainingMs: 0,
        pendingGradingStatus: 'PENDING',
      });
    } else {
      seedMockRoom(userId, {
        caseId: 1,
        status: 'CLEARED',
        durationSec: 912,
        endedAgoMs: 86_400_000,
      });
      seedMockRoom(userId, { caseId: 2, status: 'EXPIRED', endedAgoMs: 172_800_000 });
    }
    saveMockRoomDb();
  },
  /** 다른 탭에서 이어하기를 한 상황: 진행 중 세션의 연결을 바꿔, 이 탭의 다음 하트비트·조작이 409 CONNECTION_REPLACED가 되게 한다 */
  replaceConnection(roomId?: number) {
    const userId = currentUserId();
    const targets = roomId ? [findMockRoom(roomId, userId)] : listMockRooms(userId);
    targets.forEach((room) => {
      if (room?.status === 'IN_PROGRESS') {
        room.connectionId = crypto.randomUUID();
      }
    });
    saveMockRoomDb();
  },
  /** 다른 탭에서 노트를 먼저 저장한 상황: 서버 노트의 revision을 올려, 이 탭의 다음 저장이 409 NOTE_REVISION_CONFLICT가 되게 한다 */
  conflictNote(roomId: number) {
    const room = findMockRoom(roomId, currentUserId());
    if (!room) {
      throw new Error('세션을 찾을 수 없습니다.');
    }
    room.note = {
      content: '다른 화면에서 먼저 저장한 노트 내용',
      revision: room.note.revision + 1,
      updatedAtMs: currentTimeMs(),
    };
    saveMockRoomDb();
  },
  /** 목 세션·노트를 모두 지운다 (목 사용자·토큰은 그대로) */
  resetRooms() {
    resetMockRoomDb();
    saveMockRoomDb();
  },
};

declare global {
  interface Window {
    shelvedMock?: typeof shelvedMock;
  }
}

/** 브라우저 목 모드에서만 호출한다 (mocks/browser.ts) */
export function installMockDevTools() {
  window.shelvedMock = shelvedMock;
}
