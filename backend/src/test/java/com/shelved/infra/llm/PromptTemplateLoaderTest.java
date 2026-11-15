package com.shelved.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

class PromptTemplateLoaderTest {

    private final PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader(new DefaultResourceLoader());

    @Test
    void render_변수치환_공백허용_같은변수여러번() {

        // given
        Map<String, String> variables = Map.of("role", "집사", "question", "어젯밤 9시에 어디 있었나요?", "unused", "무시");

        // when
        String rendered = promptTemplateLoader.render("test-only/sample.md", variables);

        // then
        assertThat(rendered).isEqualTo("""
                너는 가상 사건 「시계탑 저택의 마지막 종소리」의 집사이다.
                질문: 어젯밤 9시에 어디 있었나요?
                다시 한 번 역할: 집사
                """);
    }

    @Test
    void render_값안의변수표기와특수문자_다시치환하지않음() {

        // given
        Map<String, String> variables = Map.of("role", "{{question}} $1 \\", "question", "질문");

        // when
        String rendered = promptTemplateLoader.render("test-only/sample.md", variables);

        // then
        assertThat(rendered).contains("의 {{question}} $1 \\이다.");
    }

    @Test
    void render_변수값누락_예외() {

        // given
        Map<String, String> variables = Map.of("role", "집사");

        // when & then
        assertThatThrownBy(() -> promptTemplateLoader.render("test-only/sample.md", variables))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("question");
    }

    @Test
    void load_파일없음_파일이름을담은예외() {

        // when & then
        assertThatThrownBy(() -> promptTemplateLoader.load("test-only/missing.md"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("prompts/test-only/missing.md");
    }

    @Test
    void load_상위경로_거부() {

        // when & then
        assertThatThrownBy(() -> promptTemplateLoader.load("../application-test.yml"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void load_캐시_같은내용반환() {

        // when
        String first = promptTemplateLoader.load("test-only/sample.md");
        String second = promptTemplateLoader.load("test-only/sample.md");

        // then
        assertThat(second).isSameAs(first);
    }
}
