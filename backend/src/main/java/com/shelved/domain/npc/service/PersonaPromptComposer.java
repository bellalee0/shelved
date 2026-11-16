package com.shelved.domain.npc.service;

import com.shelved.common.entity.json.Draft;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 초안의 Persona를 NPC 프롬프트용 텍스트(SUSPECTS.persona_prompt)로 바꾼다. 문구는 prompts/npc-persona.txt에 둔다.
 *
 * <ul>
 *     <li>템플릿은 첫 [[섹션]] 줄 앞의 머리말과, [[섹션]] 줄로 시작하는 블록들로 이루어진다. 블록의 {{value}}에 항목을 넣는다</li>
 *     <li>값이 비어 있는 섹션은 블록째 생략한다</li>
 *     <li>원문 근거(sourceRefs)는 넣지 않는다(초안에만 남김: 원문 노출·토큰 낭비 방지). 인물의 role·isCulprit은 Persona 밖이라 들어갈 수 없다
 *     (NPC가 "나는 범인"이라는 메타 인식을 갖지 않게, 행동·사실은 persona 설정으로만 전달)</li>
 * </ul>
 * 개발용 시드 반영(DevSeedImporter)과 관리자 콘텐츠 확정(W5)이 같은 변환을 쓴다
 */
@Component
@RequiredArgsConstructor
public class PersonaPromptComposer {

    static final String TEMPLATE_NAME = "npc-persona.txt";
    private static final Pattern SECTION_PATTERN = Pattern.compile("(?m)^\\[\\[([A-Za-z]+)]]\\R");
    private static final String VALUE_VARIABLE = "{{value}}";

    private final PromptTemplateLoader promptTemplateLoader;

    public String compose(Draft.Persona persona) {

        Objects.requireNonNull(persona, "persona");
        Template template = parse(promptTemplateLoader.load(TEMPLATE_NAME));

        Map<String, String> valueBySection = valuesOf(persona);
        if (!template.sectionMap().keySet().equals(valueBySection.keySet())) {
            throw new IllegalStateException("prompts/" + TEMPLATE_NAME + "의 섹션이 Persona 필드와 맞지 않습니다: "
                    + template.sectionMap().keySet());
        }

        StringBuilder composed = new StringBuilder(template.header());
        template.sectionMap().forEach((section, block) -> {
            String value = valueBySection.get(section);
            if (!value.isEmpty()) {
                composed.append(block.replace(VALUE_VARIABLE, value));
            }
        });

        return composed.toString().strip();
    }

    private static Map<String, String> valuesOf(Draft.Persona persona) {
        Map<String, String> valueMap = new LinkedHashMap<>();
        valueMap.put("speechStyle", text(persona.speechStyle()));
        valueMap.put("knownFacts", bullets(persona.knownFacts(), Draft.Fact::text));
        valueMap.put("unknownFacts", bullets(persona.unknownFacts(), Draft.Fact::text));
        valueMap.put("statements", bullets(persona.statements(), Draft.Fact::text));
        valueMap.put("hiddenFacts", bullets(persona.hiddenFacts(), Draft.Fact::text));
        valueMap.put("lies", bullets(persona.lies(), PersonaPromptComposer::lieLine));
        valueMap.put("silenceTopics", bullets(persona.silenceTopics(), Draft.Fact::text));
        valueMap.put("pressureResponse", text(persona.pressureResponse()));
        valueMap.put("statementChanges", bullets(persona.statementChanges(), PersonaPromptComposer::statementChangeLine));
        return valueMap;
    }

    private static String lieLine(Draft.Lie lie) {
        if (isBlank(lie.topic()) && isBlank(lie.lie()) && isBlank(lie.truth())) {
            return "";
        }
        return "주제: " + text(lie.topic()) + " / 거짓 진술: " + text(lie.lie()) + " / 실제 사실: " + text(lie.truth());
    }

    private static String statementChangeLine(Draft.StatementChange change) {
        if (isBlank(change.condition()) && isBlank(change.change())) {
            return "";
        }
        return "조건: " + text(change.condition()) + " / 바뀐 진술: " + text(change.change());
    }

    private static <T> String bullets(List<T> items, Function<T, String> lineOf) {
        if (items == null) {
            return "";
        }
        return items.stream()
                .filter(Objects::nonNull)
                .map(lineOf)
                .map(PersonaPromptComposer::text)
                .filter(line -> !line.isEmpty())
                .map(line -> "- " + line)
                .collect(Collectors.joining("\n"));
    }

    private static String text(String value) {
        return value == null ? "" : value.strip();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static Template parse(String raw) {
        Matcher matcher = SECTION_PATTERN.matcher(raw);
        List<int[]> markerList = new ArrayList<>();
        List<String> nameList = new ArrayList<>();
        while (matcher.find()) {
            markerList.add(new int[]{matcher.start(), matcher.end()});
            nameList.add(matcher.group(1));
        }
        if (markerList.isEmpty()) {
            throw new IllegalStateException("prompts/" + TEMPLATE_NAME + "에 섹션이 없습니다.");
        }

        String header = raw.substring(0, markerList.getFirst()[0]);
        Map<String, String> sectionMap = new LinkedHashMap<>();
        for (int i = 0; i < markerList.size(); i++) {
            int blockStart = markerList.get(i)[1];
            int blockEnd = i + 1 < markerList.size() ? markerList.get(i + 1)[0] : raw.length();
            String block = raw.substring(blockStart, blockEnd);
            if (!block.contains(VALUE_VARIABLE)) {
                throw new IllegalStateException("prompts/" + TEMPLATE_NAME + "의 섹션에 {{value}}가 없습니다: " + nameList.get(i));
            }
            if (!block.endsWith("\n")) {
                block = block + "\n";
            }
            sectionMap.put(nameList.get(i), block);
        }

        return new Template(header, sectionMap);
    }

    private record Template(String header, Map<String, String> sectionMap) {
    }
}
