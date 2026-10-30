package com.shelved.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/**
 * 관리자 사건 제작 설정값 (shelved.casebuilder)
 * 업로드 한도를 바꾸면 spring.servlet.multipart 한도도 함께 맞춘다.
 */
@ConfigurationProperties(prefix = "shelved.casebuilder")
public record CasebuilderProperties(
        DataSize sourceTextMaxSize,
        DataSize coverImageMaxSize,
        String coverImageDir
) {
}
