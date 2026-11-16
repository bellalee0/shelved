package com.shelved.domain.npc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.infra.llm.PromptTemplateLoader;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * NPC·질문 분류 프롬프트 파일을 로더가 읽어 오는지, 정해진 변수만 쓰는지 확인한다
 */
class NpcPromptFilesTest {

    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");

    private final PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader(new DefaultResourceLoader());

    static Stream<Arguments> promptFiles() {
        return Stream.of(
                Arguments.of("npc-system.txt", new String[]{"suspectName", "difficultyGuide", "glossary", "personaPrompt"}),
                Arguments.of("npc-difficulty-easy.txt", new String[]{}),
                Arguments.of("npc-difficulty-normal.txt", new String[]{}),
                Arguments.of("npc-difficulty-hard.txt", new String[]{}),
                Arguments.of("npc-persona.txt", new String[]{"value"}),
                Arguments.of("question-filter.txt", new String[]{})
        );
    }

    @ParameterizedTest
    @MethodSource("promptFiles")
    void load_프롬프트파일_읽기와변수목록(String name, String[] variables) {

        // given: name

        // when
        String template = promptTemplateLoader.load(name);

        // then
        assertThat(template).isNotBlank();
        Matcher matcher = VARIABLE_PATTERN.matcher(template);
        Set<String> used = new HashSet<>();
        while (matcher.find()) {
            used.add(matcher.group(1));
        }
        assertThat(used).containsExactlyInAnyOrder(variables);
    }
}
