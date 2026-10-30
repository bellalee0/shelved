package com.shelved.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.IntegrationTestSupport;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.unit.DataSize;

class ConfigurationIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private Clock clock;

    @Autowired
    private GameProperties gameProperties;

    @Autowired
    private NoteProperties noteProperties;

    @Autowired
    private LlmProperties llmProperties;

    @Autowired
    private CasebuilderProperties casebuilderProperties;

    @Autowired
    private AssetsProperties assetsProperties;

    @Test
    void clock_서버기준시간대_AsiaSeoul() {

        // given
        ZoneId seoul = ZoneId.of("Asia/Seoul");

        // when
        ZoneId clockZone = clock.getZone();

        // then
        assertThat(clockZone).isEqualTo(seoul);
        assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(seoul);
    }

    @Test
    void properties_잠정초기값_바인딩() {

        // given & when: application.yml의 shelved.* 값이 바인딩된 상태

        // then
        assertThat(gameProperties.timeLimit().easy()).isEqualTo(Duration.ofMinutes(20));
        assertThat(gameProperties.timeLimit().normal()).isEqualTo(Duration.ofMinutes(25));
        assertThat(gameProperties.timeLimit().hard()).isEqualTo(Duration.ofMinutes(35));
        assertThat(gameProperties.hintLimit().easy()).isEqualTo(5);
        assertThat(gameProperties.hintLimit().normal()).isEqualTo(3);
        assertThat(gameProperties.hintLimit().hard()).isEqualTo(1);
        assertThat(gameProperties.accusationLimit()).isEqualTo(3);
        assertThat(gameProperties.heartbeatInterval()).isEqualTo(Duration.ofSeconds(5));
        assertThat(gameProperties.heartbeatTimeout()).isEqualTo(Duration.ofSeconds(15));
        assertThat(gameProperties.gradingRetryMinInterval()).isEqualTo(Duration.ofSeconds(10));
        assertThat(noteProperties.maxLength()).isEqualTo(5000);
        assertThat(llmProperties.gameTimeout()).isEqualTo(Duration.ofSeconds(60));
        assertThat(llmProperties.casebuilderChatTimeout()).isEqualTo(Duration.ofSeconds(300));
        assertThat(llmProperties.processingRecoveryMargin()).isEqualTo(Duration.ofSeconds(60));
        assertThat(casebuilderProperties.coverImageMaxSize()).isEqualTo(DataSize.ofMegabytes(5));
        assertThat(casebuilderProperties.sourceTextMaxSize()).isNotNull();
        assertThat(casebuilderProperties.coverImageDir()).isNotBlank();
        assertThat(assetsProperties.basePath()).isEqualTo("../frontend/public/assets");
    }

    @Test
    void llmProperties_toString_API키마스킹() {

        // given
        LlmProperties properties = new LlmProperties(
                "sk-ant-example-secret", "model", "filter-model",
                Duration.ofSeconds(60), Duration.ofSeconds(300), Duration.ofSeconds(60), 1000, 100);

        // when
        String text = properties.toString();

        // then
        assertThat(text).doesNotContain("sk-ant-example-secret").contains("apiKey=****");
    }
}
