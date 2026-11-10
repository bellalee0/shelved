package com.shelved.domain.room.service;

import static com.shelved.common.exception.ErrorCode.ROOM_ALREADY_EXISTS;

import com.shelved.common.config.GameProperties;
import com.shelved.common.entity.Accusation;
import com.shelved.common.entity.CaseMap;
import com.shelved.common.entity.MapHotspot;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.PlayResult;
import com.shelved.common.entity.PlayerNote;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.RoomParticipant;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.User;
import com.shelved.common.enums.GradingStatus;
import com.shelved.common.enums.HotspotType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.accusation.repository.AccusationRepository;
import com.shelved.domain.cases.repository.CaseMapRepository;
import com.shelved.domain.cases.repository.EvidenceRepository;
import com.shelved.domain.cases.repository.MapHotspotRepository;
import com.shelved.domain.cases.repository.MysteryCaseRepository;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.note.repository.PlayerNoteRepository;
import com.shelved.domain.room.dto.RoomBriefingResponse;
import com.shelved.domain.room.dto.RoomCreateRequest;
import com.shelved.domain.room.dto.RoomCreateResponse;
import com.shelved.domain.room.dto.RoomMapResponse;
import com.shelved.domain.room.dto.RoomMapResponse.RoomMapEvidence;
import com.shelved.domain.room.dto.RoomMapResponse.RoomMapSuspect;
import com.shelved.domain.room.dto.RoomStatusFilter;
import com.shelved.domain.room.dto.RoomSummaryResponse;
import com.shelved.domain.room.repository.PlayResultRepository;
import com.shelved.domain.room.repository.RoomParticipantRepository;
import com.shelved.domain.room.repository.RoomRepository;
import com.shelved.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RoomService {

    private final RoomRepository roomRepository;
    private final RoomParticipantRepository roomParticipantRepository;
    private final PlayResultRepository playResultRepository;
    private final PlayerNoteRepository playerNoteRepository;
    private final AccusationRepository accusationRepository;
    private final MysteryCaseRepository mysteryCaseRepository;
    private final SuspectRepository suspectRepository;
    private final EvidenceRepository evidenceRepository;
    private final CaseMapRepository caseMapRepository;
    private final MapHotspotRepository mapHotspotRepository;
    private final UserRepository userRepository;
    private final RoomAccessService roomAccessService;
    private final GameProperties gameProperties;
    private final Clock clock;

    /**
     * 내 세션 목록. 테스트 세션 제외, 최신 생성순. 지목·결과는 페이지의 세션 ID로 한 번에 조회한다 (N+1 방지)
     */
    @Transactional(readOnly = true)
    public Page<RoomSummaryResponse> getRoomPage(AuthUser authUser, RoomStatusFilter statusFilter, Pageable pageable) {

        Page<Room> roomPage = roomRepository.findPageByParticipant(
                authUser.getId(), RoomStatusFilter.statusesOf(statusFilter), pageable);
        List<Long> roomIdList = roomPage.getContent().stream().map(Room::getId).toList();
        if (roomIdList.isEmpty()) {
            return roomPage.map(room -> toSummary(room, null, null, LocalDateTime.now(clock)));
        }

        // 같은 세션에 여러 건이면 가장 최근 지목(id가 큰 것)의 상태
        Map<Long, GradingStatus> pendingStatusByRoomId = accusationRepository
                .findAllByRoomIdInAndGradingStatusInOrderByIdAsc(roomIdList, EnumSet.of(GradingStatus.PENDING, GradingStatus.ERROR))
                .stream()
                .collect(Collectors.toMap(
                        accusation -> accusation.getRoom().getId(),
                        Accusation::getGradingStatus,
                        (older, newer) -> newer));
        Map<Long, PlayResult> playResultByRoomId = playResultRepository.findAllByRoomIdIn(roomIdList).stream()
                .collect(Collectors.toMap(playResult -> playResult.getRoom().getId(), Function.identity()));
        LocalDateTime now = LocalDateTime.now(clock);

        return roomPage.map(room -> toSummary(
                room, pendingStatusByRoomId.get(room.getId()), playResultByRoomId.get(room.getId()), now));
    }

    /**
     * READY 세션 생성. 같은 사용자의 같은 사건 미종료 세션은 하나만 허용한다.
     * 동시 요청 직렬화: 검사 전에 요청자 users 행을 PESSIMISTIC_WRITE로 잠근다
     * (미종료 세션 부분 UNIQUE 인덱스는 ddl-auto로 만들 수 없음).
     */
    @Transactional
    public RoomCreateResponse createRoom(AuthUser authUser, RoomCreateRequest request) {

        MysteryCase mysteryCase = mysteryCaseRepository.findPublishedCaseById(request.caseId());
        User user = userRepository.findUserWithLockById(authUser.getId());

        roomRepository.findLatestRoomIdByUserAndCase(user.getId(), mysteryCase.getId(), RoomStatusFilter.ACTIVE.statuses())
                .ifPresent(roomId -> {
                    throw new CustomException(ROOM_ALREADY_EXISTS, Map.of("roomId", roomId));
                });

        LocalDateTime now = LocalDateTime.now(clock);
        int timeLimitSec = Math.toIntExact(gameProperties.timeLimitOf(mysteryCase.getDifficulty()).toSeconds());
        Room room = roomRepository.save(new Room(mysteryCase, user, false, timeLimitSec, now));
        roomParticipantRepository.save(new RoomParticipant(room, user));
        playerNoteRepository.save(new PlayerNote(room, user, now));

        return RoomCreateResponse.from(room);
    }

    /**
     * 사전 정보. 모든 상태에서 조회할 수 있다
     */
    @Transactional(readOnly = true)
    public RoomBriefingResponse getRoomBriefing(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoom(roomId, authUser);

        List<Suspect> suspectList = suspectRepository.findAllByMysteryCaseIdOrderByIdAsc(room.getMysteryCase().getId());

        return RoomBriefingResponse.from(room, gameProperties, suspectList);
    }

    /**
     * 맵 설정과 배치. suspects는 사건의 모든 인물(미배치는 x·y null), evidences는 배치된 물증만
     */
    @Transactional(readOnly = true)
    public RoomMapResponse getRoomMap(AuthUser authUser, Long roomId) {

        Room room = roomAccessService.getAccessibleRoom(roomId, authUser);
        Long caseId = room.getMysteryCase().getId();
        CaseMap caseMap = caseMapRepository.findCaseMapByCaseId(caseId);

        List<MapHotspot> hotspotList = mapHotspotRepository.findAllByMysteryCaseId(caseId);
        Map<Long, MapHotspot> suspectHotspotById = hotspotsByRefId(hotspotList, HotspotType.SUSPECT);
        Map<Long, MapHotspot> evidenceHotspotById = hotspotsByRefId(hotspotList, HotspotType.EVIDENCE);

        List<RoomMapSuspect> suspectList = suspectRepository.findAllByMysteryCaseIdOrderByIdAsc(caseId).stream()
                .map(suspect -> RoomMapSuspect.from(suspect, suspectHotspotById.get(suspect.getId())))
                .toList();
        List<RoomMapEvidence> evidenceList = evidenceRepository.findAllByMysteryCaseIdOrderByIdAsc(caseId).stream()
                .filter(evidence -> evidenceHotspotById.containsKey(evidence.getId()))
                .map(evidence -> RoomMapEvidence.from(evidence, evidenceHotspotById.get(evidence.getId())))
                .toList();

        return RoomMapResponse.from(caseMap, suspectList, evidenceList);
    }

    private static Map<Long, MapHotspot> hotspotsByRefId(List<MapHotspot> hotspotList, HotspotType type) {
        return hotspotList.stream()
                .filter(hotspot -> hotspot.getType() == type)
                .collect(Collectors.toMap(MapHotspot::getRefId, Function.identity()));
    }

    private static RoomSummaryResponse toSummary(
            Room room,
            GradingStatus pendingGradingStatus,
            PlayResult playResult,
            LocalDateTime now
    ) {
        MysteryCase mysteryCase = room.getMysteryCase();
        long remainingMs = RoomTimeCalculator.calculate(room, now).remainingMs();

        return new RoomSummaryResponse(
                room.getId(),
                mysteryCase.getId(),
                mysteryCase.getDisplayTitle(),
                mysteryCase.getDifficulty(),
                mysteryCase.getCoverImageUrl(),
                room.getStatus(),
                remainingMs,
                pendingGradingStatus,
                playResult == null ? null : playResult.getEndedAt(),
                playResult == null ? null : playResult.getDurationSec(),
                room.getCreatedAt()
        );
    }
}
