package com.shelved.domain.room.controller;

import static com.shelved.common.enums.SuccessMessage.ROOM_BRIEFING_GET_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.ROOM_CREATE_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.ROOM_LIST_GET_SUCCESS;
import static com.shelved.common.enums.SuccessMessage.ROOM_MAP_GET_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.dto.PageResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.room.dto.RoomBriefingResponse;
import com.shelved.domain.room.dto.RoomCreateRequest;
import com.shelved.domain.room.dto.RoomCreateResponse;
import com.shelved.domain.room.dto.RoomMapResponse;
import com.shelved.domain.room.dto.RoomStatusFilter;
import com.shelved.domain.room.dto.RoomSummaryResponse;
import com.shelved.domain.room.service.RoomService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "세션", description = "플레이 세션 생성·목록·사전 정보·맵")
@RestController
@RequestMapping("/api/rooms")
@RequiredArgsConstructor
public class RoomController {

    private final RoomService roomService;

    @Operation(summary = "내 세션 목록", description = "이어하기·결과 보기 목록. 테스트 세션 제외, 최신 생성순. status: ACTIVE | ENDED, 생략 시 전체")
    @GetMapping
    public ResponseEntity<CommonResponse<PageResponse<RoomSummaryResponse>>> getRoomPage(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(required = false) RoomStatusFilter status,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 정렬은 최신 생성순으로 고정한다 (요청의 sort는 쓰지 않음)
        Pageable unsorted = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
        Page<RoomSummaryResponse> roomPage = roomService.getRoomPage(authUser, status, unsorted);
        PageResponse<RoomSummaryResponse> response = PageResponse.from(roomPage);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(ROOM_LIST_GET_SUCCESS, response));
    }

    @Operation(summary = "세션 생성", description = "공개된 사건으로 READY 세션을 만든다. 같은 사건의 미종료 세션이 있으면 409 ROOM_ALREADY_EXISTS (detail.roomId)")
    @PostMapping
    public ResponseEntity<CommonResponse<RoomCreateResponse>> createRoom(
            @AuthenticationPrincipal AuthUser authUser,
            @Valid @RequestBody RoomCreateRequest request
    ) {
        RoomCreateResponse response = roomService.createRoom(authUser, request);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(CommonResponse.success(ROOM_CREATE_SUCCESS, response));
    }

    @Operation(summary = "사전 정보", description = "모든 상태에서 조회할 수 있다. 원작·범인·트릭·용어집은 포함하지 않는다.")
    @GetMapping("/{roomId}/briefing")
    public ResponseEntity<CommonResponse<RoomBriefingResponse>> getRoomBriefing(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId
    ) {
        RoomBriefingResponse response = roomService.getRoomBriefing(authUser, roomId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(ROOM_BRIEFING_GET_SUCCESS, response));
    }

    @Operation(summary = "맵 설정과 배치", description = "suspects는 지목 대상 전체(미배치 인물은 x·y null), evidences는 배치된 물증. 물증 설명·범인 여부는 포함하지 않는다.")
    @GetMapping("/{roomId}/map")
    public ResponseEntity<CommonResponse<RoomMapResponse>> getRoomMap(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long roomId
    ) {
        RoomMapResponse response = roomService.getRoomMap(authUser, roomId);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(ROOM_MAP_GET_SUCCESS, response));
    }
}
