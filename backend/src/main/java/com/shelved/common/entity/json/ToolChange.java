package com.shelved.common.entity.json;

/**
 * 제작 채팅에서 성공한 AI tool 호출의 변경 요약 (ADMIN_CASE_MESSAGES.tool_changes).
 * targetKey는 set_* tool이면 null, 용어집이면 original
 */
public record ToolChange(
        String tool,
        String targetKey,
        String summary
) {
}
