package com.shelved.domain.ranking.controller;

import static com.shelved.common.enums.SuccessMessage.RANKING_GET_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.jwt.AuthUser;
import com.shelved.domain.ranking.dto.RankingGetResponse;
import com.shelved.domain.ranking.service.RankingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "랭킹", description = "사건별 랭킹")
@RestController
@RequestMapping("/api/rankings")
@RequiredArgsConstructor
public class RankingController {

    private static final int MAX_PAGE_SIZE = 100;

    private final RankingService rankingService;

    @Operation(summary = "사건별 랭킹", description = "공개 사건의 사용자별 최고 성공 기록(테스트 세션 제외). 진행 시간순 공동 순위(1·2·2·4), "
            + "같은 순위는 먼저 달성한 순. myRanking은 요청자의 전체 기준 순위(기록이 없으면 null). size 기본 20, 최대 100. "
            + "비공개·없는 사건 404 CASE_NOT_FOUND")
    @GetMapping("/{caseId}")
    public ResponseEntity<CommonResponse<RankingGetResponse>> getRanking(
            @AuthenticationPrincipal AuthUser authUser,
            @PathVariable Long caseId,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        // 정렬은 순위순으로 고정한다 (요청의 sort는 쓰지 않음)
        Pageable limited = PageRequest.of(pageable.getPageNumber(), Math.min(pageable.getPageSize(), MAX_PAGE_SIZE));
        RankingGetResponse response = rankingService.getRanking(authUser.getId(), caseId, limited);

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(RANKING_GET_SUCCESS, response));
    }
}
