package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 개인 노트 설정값 (shelved.note)
 *
 * @param maxLength 최대 글자 수 (UTF-16 코드 단위, String.length())
 */
@ConfigurationProperties(prefix = "shelved.note")
public record NoteProperties(
        int maxLength
) {
}
