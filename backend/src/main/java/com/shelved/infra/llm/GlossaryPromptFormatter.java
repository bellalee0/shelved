package com.shelved.infra.llm;

import com.shelved.common.entity.json.GlossaryTerm;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 프롬프트의 "고유명사 표기" 블록. NPC·힌트·보충 질문 프롬프트가 같은 형식을 쓴다
 */
public final class GlossaryPromptFormatter {

    static final String NO_GLOSSARY = "(없음)";

    private GlossaryPromptFormatter() {
    }

    /**
     * "- 원어 → 한국어 (메모)" 한 줄씩. 비어 있으면 "(없음)"
     */
    public static String format(List<GlossaryTerm> glossary) {
        if (glossary == null || glossary.isEmpty()) {
            return NO_GLOSSARY;
        }
        return glossary.stream()
                .map(term -> "- " + term.original() + " → " + term.korean()
                        + (term.note() == null || term.note().isBlank() ? "" : " (" + term.note() + ")"))
                .collect(Collectors.joining("\n"));
    }
}
