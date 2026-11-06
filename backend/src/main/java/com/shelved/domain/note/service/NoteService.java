package com.shelved.domain.note.service;

import static com.shelved.common.exception.ErrorCode.INVALID_INPUT;
import static com.shelved.common.exception.ErrorCode.NOTE_REVISION_CONFLICT;

import com.shelved.common.config.NoteProperties;
import com.shelved.common.entity.PlayerNote;
import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.note.dto.NoteResponse;
import com.shelved.domain.note.dto.NoteUpdateRequest;
import com.shelved.domain.note.repository.PlayerNoteRepository;
import com.shelved.domain.room.service.RoomAccessService;
import com.shelved.domain.room.service.RoomPlayService;
import com.shelved.domain.room.service.RoomTimeSettler;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 개인 노트. 내용은 로그·예외 메시지에 남기지 않는다
 */
@Service
@RequiredArgsConstructor
public class NoteService {

    private final RoomAccessService roomAccessService;
    private final RoomPlayService roomPlayService;
    private final RoomTimeSettler roomTimeSettler;
    private final PlayerNoteRepository playerNoteRepository;
    private final NoteProperties noteProperties;
    private final Clock clock;

    /**
     * 내 노트 조회. 모든 상태에서 가능하다. editable 계산을 위해 getRoomState와 같이 세션 행을 잠그고 정산(1·2)만 적용한다
     */
    @Transactional
    public NoteResponse getMyNote(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoomForUpdate(roomId, authUser);
        LocalDateTime now = LocalDateTime.now(clock);
        roomTimeSettler.settle(room, now);

        PlayerNote note = playerNoteRepository.findPlayerNote(roomId, authUser.getId());

        return toResponse(note, isEditable(room, now));
    }

    /**
     * 내 노트 저장. revision이 같을 때만 원문 그대로 저장하고 revision을 1 올린다 (backend.md 16)
     */
    @Transactional
    public NoteResponse updateMyNote(AuthUser authUser, Long roomId, String connectionId, NoteUpdateRequest request) {

        Room room = roomPlayService.acquirePlayableRoom(roomId, authUser, connectionId);
        LocalDateTime now = LocalDateTime.now(clock);

        // 아래 400·409로 거부하면 공통 검사의 last_seen_at 갱신도 롤백된다. 하트비트가 5초마다 갱신하므로 받아들인다
        String content = request.content();
        if (content.length() > noteProperties.maxLength()) {
            throw new CustomException(INVALID_INPUT, Map.of("field", "content"));
        }

        PlayerNote note = playerNoteRepository.findPlayerNote(roomId, authUser.getId());
        if (request.revision() != note.getRevision()) {
            throw new CustomException(NOTE_REVISION_CONFLICT, Map.of(
                    "currentRevision", note.getRevision(),
                    "currentContent", note.getContent()));
        }
        note.update(content, now);

        return toResponse(note, isEditable(room, now));
    }

    private NoteResponse toResponse(PlayerNote note, boolean editable) {
        return new NoteResponse(note.getContent(), note.getRevision(), note.getUpdatedAt(), noteProperties.maxLength(), editable);
    }

    // 유예 구간(이탈 확정 전)에서 시간이 소진됐으면 IN_PROGRESS여도 false
    private boolean isEditable(Room room, LocalDateTime now) {
        return room.getStatus() == RoomStatus.IN_PROGRESS && !roomTimeSettler.isTimeUp(room, now);
    }
}
