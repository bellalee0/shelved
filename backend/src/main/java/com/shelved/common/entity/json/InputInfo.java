package com.shelved.common.entity.json;

import com.shelved.common.enums.ChatInputMode;

/**
 * 제작 채팅 AI 답변을 만들 때 입력을 어떻게 구성했는지 (ADMIN_CASE_MESSAGES.input_info)
 */
public record InputInfo(
        ChatInputMode mode,
        Integer inputTokens,
        String note
) {
}
