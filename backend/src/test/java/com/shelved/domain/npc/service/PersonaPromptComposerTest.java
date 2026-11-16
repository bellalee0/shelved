package com.shelved.domain.npc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.entity.json.Draft;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * persona → persona_prompt 변환. 가상 사건 「시계탑 저택의 마지막 종소리」의 초안만 쓴다
 */
class PersonaPromptComposerTest {

    private final PersonaPromptComposer composer =
            new PersonaPromptComposer(new PromptTemplateLoader(new DefaultResourceLoader()));

    @Test
    void compose_모든섹션_내용포함_근거와역할미포함() {

        // given
        Draft.SourceRef sourceRef = new Draft.SourceRef("3장 2문단", "종소리가 아홉 번 울렸다는 원문 인용");
        Draft.Persona persona = new Draft.Persona(
                "정중하고 짧게 말한다",
                List.of(new Draft.Fact("서재에 녹음기가 있다는 것을 안다", List.of(sourceRef))),
                List.of(new Draft.Fact("주인이 숨진 정확한 시각은 모른다", List.of())),
                List.of(new Draft.Fact("9시에는 주방에서 은식기를 닦고 있었다", List.of(sourceRef))),
                List.of(new Draft.Fact("8시 50분에 서재에 들어갔다", List.of())),
                List.of(new Draft.Lie("9시의 행적", "주방에 있었다", "서재에서 녹음기를 설치했다", List.of(sourceRef))),
                List.of(new Draft.Fact("주인의 유언장", List.of())),
                "모순을 추궁받아도 자백하지 않고 답변을 거부한다",
                List.of(new Draft.StatementChange("태엽 장치를 보여 주면", "서재에 들어간 사실은 인정한다", List.of(sourceRef)))
        );

        // when
        String composed = composer.compose(persona);

        // then
        assertThat(composed).contains(
                "### 말투\n정중하고 짧게 말한다",
                "### 아는 사실",
                "- 서재에 녹음기가 있다는 것을 안다",
                "### 모르는 사실",
                "- 주인이 숨진 정확한 시각은 모른다",
                "### 기본 진술",
                "- 9시에는 주방에서 은식기를 닦고 있었다",
                "### 숨기는 사실",
                "- 8시 50분에 서재에 들어갔다",
                "### 정해진 거짓말",
                "- 주제: 9시의 행적 / 거짓 진술: 주방에 있었다 / 실제 사실: 서재에서 녹음기를 설치했다",
                "### 침묵 주제",
                "- 주인의 유언장",
                "### 추궁 반응\n모순을 추궁받아도 자백하지 않고 답변을 거부한다",
                "### 진술 변경 조건",
                "- 조건: 태엽 장치를 보여 주면 / 바뀐 진술: 서재에 들어간 사실은 인정한다");
        assertThat(composed).doesNotContain("3장 2문단", "원문 인용", "sourceRefs", "{{", "[[");
        assertThat(composed.indexOf("### 말투")).isLessThan(composed.indexOf("### 아는 사실"));
        assertThat(composed.indexOf("### 추궁 반응")).isLessThan(composed.indexOf("### 진술 변경 조건"));
    }

    @Test
    void compose_빈항목_섹션째생략() {

        // given: 가정부 — 기본 진술·추궁 반응만 있고 나머지는 빈 배열·공백·null
        Draft.Persona persona = new Draft.Persona(
                "차분하고 자세히 말한다",
                List.of(),
                null,
                List.of(new Draft.Fact("9시 15분쯤 서재에서 주인의 목소리를 들었다", List.of()),
                        new Draft.Fact("  ", List.of())),
                List.of(),
                List.of(),
                List.of(),
                "모르는 것은 모른다고 답한다",
                List.of()
        );

        // when
        String composed = composer.compose(persona);

        // then
        assertThat(composed).contains("### 말투", "### 기본 진술", "### 추궁 반응",
                "- 9시 15분쯤 서재에서 주인의 목소리를 들었다");
        assertThat(composed).doesNotContain("### 아는 사실", "### 모르는 사실", "### 숨기는 사실",
                "### 정해진 거짓말", "### 침묵 주제", "### 진술 변경 조건", "{{value}}");
        assertThat(composed).doesNotContain("- \n").doesNotContainPattern("(?m)^- $");
    }

    @Test
    void compose_초안의인물_role과범인여부미포함() {

        // given: 범인으로 설정된 집사 (role: "저택의 집사. 20년째 근무")
        Draft.SuspectItem butler = ClocktowerCaseFixture.draft().suspects().getFirst();

        // when
        String composed = composer.compose(butler.persona());

        // then
        assertThat(butler.isCulprit()).isTrue();
        assertThat(composed).doesNotContain(butler.role(), "20년째", "범인", "isCulprit", "culprit");
        assertThat(composed).contains("정중하고 짧게 말한다");
    }
}
