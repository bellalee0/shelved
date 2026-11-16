package com.shelved.domain.airequest.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import tools.jackson.databind.json.JsonMapper;

class QuestionFilterResponseParserTest {

    private final QuestionFilterResponseParser parser = new QuestionFilterResponseParser(JsonMapper.builder().build());

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"blocked\": true}",
            "  {\"blocked\":true}\n",
            "```json\n{\"blocked\": true}\n```",
            "```\n{\"blocked\": true}\n```",
            "```{\"blocked\": true}```",
            "{\"blocked\": true, \"reason\": \"무시하는 필드\"}"
    })
    void parseBlocked_차단JSON_true(String text) {

        // given: text

        // when
        boolean blocked = parser.parseBlocked(text);

        // then
        assertThat(blocked).isTrue();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"blocked\": false}",
            "```json\n{\"blocked\": false}\n```"
    })
    void parseBlocked_허용JSON_false(String text) {

        // given: text

        // when
        boolean blocked = parser.parseBlocked(text);

        // then
        assertThat(blocked).isFalse();
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
            "   ",
            "차단합니다",
            "blocked: true",
            "{\"blocked\": \"true\"}",
            "{\"blocked\": 1}",
            "{\"blocked\": null}",
            "{\"result\": true}",
            "[{\"blocked\": true}]",
            "true",
            "{\"blocked\": true} 이 질문은 범인을 묻습니다.",
            "판정: {\"blocked\": true}",
            "{\"blocked\": tru"
    })
    void parseBlocked_형식오류_AiResponseInvalidException(String text) {

        // given: text

        // when & then
        assertThatThrownBy(() -> parser.parseBlocked(text))
                .isInstanceOf(AiResponseInvalidException.class);
    }
}
