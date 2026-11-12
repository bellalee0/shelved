package com.shelved.infra.llm;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

/**
 * src/main/resources/prompts/ 아래 프롬프트 파일을 읽고 {{변수}}를 치환한다 (backend.md: 긴 프롬프트를 Java 문자열로 쓰지 않는다).
 * - 파일은 처음 사용할 때 읽어 캐시한다. 파일이 없으면 첫 사용 시 파일 이름을 담은 IllegalStateException
 * - 템플릿의 변수에 값이 주어지지 않으면 IllegalStateException (빈 문자열로 조용히 바꾸지 않는다). 남는 값은 무시한다
 * - 치환은 한 번만 한다. 값 안의 {{...}}는 다시 치환하지 않는다
 *
 * <p>프롬프트 파일 작성 규칙: 저장소에 커밋되므로 사건과 무관한 일반 지시(역할·응답 형식·난이도별 응답 방식 등)만 담는다.
 * 인물 설정·용어집·대화 이력·정답 등 사건별 내용은 파일에 쓰지 않고 실행 시 DB에서 읽어 변수로 넣는다.
 * 프롬프트 전문과 치환 결과는 로그에 남기지 않는다.</p>
 */
@Component
public class PromptTemplateLoader {

    static final String DEFAULT_BASE_LOCATION = "classpath:prompts/";
    private static final Pattern VARIABLE_PATTERN = Pattern.compile("\\{\\{\\s*([A-Za-z0-9_]+)\\s*}}");
    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_\\-]+(/[A-Za-z0-9_\\-]+)*\\.[A-Za-z0-9]+");

    private final ResourceLoader resourceLoader;
    private final String baseLocation;
    private final Map<String, String> templateCache = new ConcurrentHashMap<>();

    @Autowired
    public PromptTemplateLoader(ResourceLoader resourceLoader) {
        this(resourceLoader, DEFAULT_BASE_LOCATION);
    }

    PromptTemplateLoader(ResourceLoader resourceLoader, String baseLocation) {
        this.resourceLoader = resourceLoader;
        this.baseLocation = baseLocation;
    }

    /**
     * 템플릿 원문
     *
     * @param name prompts/ 기준 상대 경로 (예: "npc/system.md")
     */
    public String load(String name) {
        validateName(name);
        return templateCache.computeIfAbsent(name, this::read);
    }

    /**
     * 템플릿을 읽고 {{변수}}를 치환한다
     */
    public String render(String name, Map<String, String> variables) {

        String template = load(name);
        Matcher matcher = VARIABLE_PATTERN.matcher(template);

        Set<String> missingSet = new LinkedHashSet<>();
        StringBuilder rendered = new StringBuilder();
        while (matcher.find()) {
            String variable = matcher.group(1);
            String value = variables.get(variable);
            if (value == null) {
                missingSet.add(variable);
                continue;
            }
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(value));
        }
        if (!missingSet.isEmpty()) {
            throw new IllegalStateException("프롬프트 변수 값이 없습니다: " + name + " " + missingSet);
        }
        matcher.appendTail(rendered);

        return rendered.toString();
    }

    private String read(String name) {
        Resource resource = resourceLoader.getResource(baseLocation + name);
        if (!resource.exists()) {
            throw new IllegalStateException("프롬프트 파일을 찾을 수 없습니다: prompts/" + name);
        }
        try (InputStream inputStream = resource.getInputStream()) {
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("프롬프트 파일을 읽을 수 없습니다: prompts/" + name, e);
        }
    }

    // 상위 경로(..)·절대 경로로 prompts/ 밖을 읽지 못하게 한다
    private static void validateName(String name) {
        if (name == null || !NAME_PATTERN.matcher(name).matches()) {
            throw new IllegalArgumentException("프롬프트 파일 이름이 올바르지 않습니다: " + name);
        }
    }
}
