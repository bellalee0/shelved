package com.shelved.common.entity.json;

/**
 * 고유명사 용어집 항목 (CASES.glossary, 초안 glossary). original이 고유 키
 */
public record GlossaryTerm(
        String original,
        String korean,
        String note
) {
}
