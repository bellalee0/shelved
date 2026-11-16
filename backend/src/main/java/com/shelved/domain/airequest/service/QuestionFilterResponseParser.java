package com.shelved.domain.airequest.service;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 질문 분류 응답의 형식 검증. 허용하는 형식은 이 한 곳에서 정한다
 *
 * <ul>
 *     <li>JSON 객체 1개이고 blocked가 boolean이어야 한다. 다른 필드는 무시한다</li>
 *     <li>앞뒤 공백과, 객체 전체를 감싼 코드 블록(``` 또는 ```json)은 허용한다</li>
 *     <li>그 밖의 문장이 붙었거나, JSON이 아니거나, blocked가 없거나 boolean이 아니면 형식 오류(AiResponseInvalidException)</li>
 * </ul>
 * 예외 메시지에는 응답 본문을 넣지 않는다
 */
public final class QuestionFilterResponseParser {

    private static final String CODE_FENCE = "```";
    private static final String BLOCKED_FIELD = "blocked";

    private final JsonMapper jsonMapper;

    public QuestionFilterResponseParser(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    /**
     * @return 차단이면 true
     */
    public boolean parseBlocked(String text) {

        if (text == null || text.isBlank()) {
            throw new AiResponseInvalidException("분류 응답이 비어 있음");
        }

        JsonNode root;
        try {
            root = jsonMapper.readTree(stripCodeFence(text.strip()));
        } catch (JacksonException e) {
            throw new AiResponseInvalidException("분류 응답이 JSON이 아님");
        }
        if (root == null || !root.isObject()) {
            throw new AiResponseInvalidException("분류 응답이 JSON 객체가 아님");
        }

        JsonNode blocked = root.get(BLOCKED_FIELD);
        if (blocked == null || !blocked.isBoolean()) {
            throw new AiResponseInvalidException("분류 응답의 blocked가 boolean이 아님");
        }

        return blocked.booleanValue();
    }

    // 객체 전체를 감싼 코드 블록만 벗긴다. 여는 줄의 언어 표기(json 등)는 버린다
    private static String stripCodeFence(String text) {
        if (!text.startsWith(CODE_FENCE) || !text.endsWith(CODE_FENCE) || text.length() < CODE_FENCE.length() * 2) {
            return text;
        }
        String inner = text.substring(CODE_FENCE.length(), text.length() - CODE_FENCE.length());
        int firstLineEnd = inner.indexOf('\n');
        if (firstLineEnd < 0) {
            return inner.strip();
        }
        String firstLine = inner.substring(0, firstLineEnd).strip();
        if (!firstLine.isEmpty() && !firstLine.chars().allMatch(Character::isLetter)) {
            return inner.strip();
        }
        return inner.substring(firstLineEnd + 1).strip();
    }
}
