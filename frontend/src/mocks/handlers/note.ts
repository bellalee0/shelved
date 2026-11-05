import { http } from 'msw';
import type { NoteResponse, NoteUpdateRequest } from '../../features/note/types';
import { MOCK_GAME_CONFIG } from '../data/clocktowerCase';
import { checkOperable, loadOwnedRoom } from '../roomAccess';
import {
  currentTimeMs,
  isNoteEditable,
  saveMockRoomDb,
  toServerTime,
  type MockRoom,
} from '../roomDb';
import { checkScenario } from '../scenarios';
import { mockErrorResponse, ok } from './auth';

// docs/api/investigation.md 의 개인 노트 조회·저장. content는 trim·정규화 없이 원문 그대로 저장한다.

const API_BASE_URL = import.meta.env.VITE_API_BASE_URL;

function toNoteResponse(room: MockRoom): NoteResponse {
  return {
    content: room.note.content,
    revision: room.note.revision,
    updatedAt: toServerTime(room.note.updatedAtMs),
    maxLength: MOCK_GAME_CONFIG.noteMaxLength,
    editable: isNoteEditable(room),
  };
}

export const noteHandlers = [
  // 조회는 모든 상태에서 가능하다
  http.get(`${API_BASE_URL}/api/rooms/:roomId/notes/me`, ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    return ok(toNoteResponse(loaded.room), '노트 조회 성공');
  }),

  http.put(`${API_BASE_URL}/api/rooms/:roomId/notes/me`, async ({ request, params }) => {
    const scenarioResponse = checkScenario('other');
    if (scenarioResponse) {
      return scenarioResponse;
    }
    const loaded = loadOwnedRoom(request, params.roomId);
    if ('error' in loaded) {
      return loaded.error;
    }
    saveMockRoomDb();
    const operableError = checkOperable(request, loaded);
    if (operableError) {
      return operableError;
    }

    const { room } = loaded;
    const body = (await request.json().catch(() => ({}))) as Partial<NoteUpdateRequest>;
    if (typeof body.content !== 'string') {
      return mockErrorResponse(400, 'INVALID_INPUT', '노트 내용은 필수입니다.', {
        field: 'content',
      });
    }
    if (!Number.isInteger(body.revision) || (body.revision ?? -1) < 0) {
      return mockErrorResponse(400, 'INVALID_INPUT', '노트 버전은 0 이상이어야 합니다.', {
        field: 'revision',
      });
    }
    // 길이는 Java/JavaScript와 같은 UTF-16 코드 단위로 센다 (String.length)
    if (body.content.length > MOCK_GAME_CONFIG.noteMaxLength) {
      return mockErrorResponse(
        400,
        'INVALID_INPUT',
        `노트는 ${MOCK_GAME_CONFIG.noteMaxLength}자까지 작성할 수 있습니다.`,
        { field: 'content' },
      );
    }
    if (body.revision !== room.note.revision) {
      return mockErrorResponse(
        409,
        'NOTE_REVISION_CONFLICT',
        '다른 곳에서 노트가 먼저 저장되었습니다. 서버 내용과 작성 중인 내용을 확인해 주세요.',
        { currentRevision: room.note.revision, currentContent: room.note.content },
      );
    }

    room.note = {
      content: body.content,
      revision: room.note.revision + 1,
      updatedAtMs: currentTimeMs(),
    };
    saveMockRoomDb();
    return ok(toNoteResponse(room), '노트 저장 성공');
  }),
];
