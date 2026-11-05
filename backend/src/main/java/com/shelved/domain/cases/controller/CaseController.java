package com.shelved.domain.cases.controller;

import static com.shelved.common.enums.SuccessMessage.CASE_LIST_GET_SUCCESS;

import com.shelved.common.dto.CommonResponse;
import com.shelved.domain.cases.dto.CaseCardResponse;
import com.shelved.domain.cases.service.CaseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "사건", description = "사건 카드 목록")
@RestController
@RequestMapping("/api/cases")
@RequiredArgsConstructor
public class CaseController {

    private final CaseService caseService;

    @Operation(summary = "사건 카드 목록", description = "공개된 사건만 반환한다. 로그인하지 않아도 호출할 수 있다.")
    @GetMapping
    public ResponseEntity<CommonResponse<List<CaseCardResponse>>> getCaseList() {

        List<CaseCardResponse> response = caseService.getCaseList();

        return ResponseEntity.status(HttpStatus.OK)
                .body(CommonResponse.success(CASE_LIST_GET_SUCCESS, response));
    }
}
