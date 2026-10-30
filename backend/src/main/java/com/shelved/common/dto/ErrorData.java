package com.shelved.common.dto;

/**
 * 실패 응답의 data
 *
 * @param code   ErrorCode enum 이름
 * @param detail 일부 예외에서만 제공하는 추가 정보, 없으면 null (형식은 docs/api/errors.md)
 */
public record ErrorData(
        String code,
        Object detail
) {
}
