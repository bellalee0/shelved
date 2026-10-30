package com.shelved.common.dto;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * 페이지 조회 API의 data (docs/api/README.md PageResponse)
 * Spring의 Page를 그대로 직렬화하지 않고 이 형식으로 변환한다.
 */
public record PageResponse<T>(
        List<T> content,
        long totalElements,
        int totalPages,
        int size,
        int number
) {

    public static <T> PageResponse<T> from(Page<T> page) {
        return new PageResponse<>(
                page.getContent(),
                page.getTotalElements(),
                page.getTotalPages(),
                page.getSize(),
                page.getNumber()
        );
    }
}
