package com.shelved.domain.hint.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.config.LlmProperties;
import com.shelved.common.enums.LlmProvider;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * 힌트·보충 질문 요청 구성. 가상 사건 「시계탑 저택의 마지막 종소리」만 쓴다
 */
class HintPromptBuilderTest {

    private static final int MAX_OUTPUT_TOKENS = 777;
    private static final String QUESTION = "종소리가 울린 시각을 어떻게 봐야 할까요?";
    private static final String FOLLOWUP_QUESTION = "시각을 뒷받침한다는 게 무슨 뜻이에요?";
    private static final String HINT_CONTENT = "마거릿은 9시 15분쯤 주인의 목소리를 들었다고 말했습니다.";

    private final PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader(new DefaultResourceLoader());
    private final HintPromptBuilder builder = new HintPromptBuilder(promptTemplateLoader, llmProperties());

    @Test
    void buildHint_사건정보와정답참고와수사기록과이전힌트_변수주입() {

        // given
        HintContext context = hintContext(
                List.of("찢긴 메모"),
                List.of(new HintContext.NpcConversation("마거릿", List.of(
                        new HintContext.Message(true, "9시 15분에 뭘 들었나요?"),
                        new HintContext.Message(false, "서재에서 주인님 목소리를 들었어요.")))),
                List.of(new HintContext.PreviousHint("누구부터 만나야 하나요?", "저택 사람들의 행적부터 들어 보세요.")));

        // when
        LlmRequest request = builder.buildHint(context);

        // then
        assertThat(request.requestType()).isEqualTo(LlmRequestType.HINT);
        assertThat(request.modelTier()).isEqualTo(LlmModelTier.MAIN);
        assertThat(request.maxOutputTokens()).isEqualTo(MAX_OUTPUT_TOKENS);
        assertThat(request.messageList()).containsExactly(LlmMessage.user(QUESTION));
        assertThat(request.systemPrompt())
                .contains("제목: " + ClocktowerCaseFixture.DISPLAY_TITLE,
                        "사건 소개: " + ClocktowerCaseFixture.INTRODUCTION,
                        "수사 목표: " + ClocktowerCaseFixture.INVESTIGATION_GOAL,
                        "- Harris → 해리스 (집사)",
                        "- 해리스: 저택의 집사\n- 마거릿: 저택의 가정부\n- 엘리스",
                        "- 서재의 작은 상자: 책장 뒤에서",
                        "범인: 해리스\n범행 방법 요약: " + ClocktowerCaseFixture.TRICK_SUMMARY,
                        "범행 방법의 핵심 요소:\n- " + ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
                        "### 조사한 물증\n- 찢긴 메모\n",
                        "#### 대화 상대: 마거릿\n- 플레이어의 질문: 9시 15분에 뭘 들었나요?\n- 마거릿의 진술: 서재에서 주인님 목소리를 들었어요.",
                        "1. 질문: 누구부터 만나야 하나요?\n   답변: 저택 사람들의 행적부터 들어 보세요.",
                        "범인의 이름이나 범행 방법을 그대로 말하지 않는다",
                        "확정된 사실처럼 말하지 않는다")
                .doesNotContain("{{", "culprit", "isCulprit", QUESTION);
    }

    @Test
    void buildHint_수사기록과이전힌트가없으면_없음표시() {

        // given
        HintContext context = hintContext(List.of(), List.of(), List.of());

        // when
        String systemPrompt = builder.buildHint(context).systemPrompt();

        // then
        assertThat(systemPrompt)
                .contains("### 조사한 물증\n(없음)", "인물별 최근 대화 원문. 인물의 진술은 거짓이거나 틀릴 수 있다)\n(없음)",
                        "## 이전 힌트 (오래된 순)\n(없음)");
    }

    @Test
    void buildFollowup_원래질문과힌트답변과용어집만_정답과수사기록은없음() {

        // given
        HintFollowupContext context = new HintFollowupContext(
                "req-1", 10L, ClocktowerCaseFixture.GLOSSARY, QUESTION, HINT_CONTENT, FOLLOWUP_QUESTION);

        // when
        LlmRequest request = builder.buildFollowup(context);

        // then
        assertThat(request.requestType()).isEqualTo(LlmRequestType.HINT_FOLLOWUP);
        assertThat(request.modelTier()).isEqualTo(LlmModelTier.MAIN);
        assertThat(request.messageList()).containsExactly(LlmMessage.user(FOLLOWUP_QUESTION));
        assertThat(request.systemPrompt())
                .contains("## 원래 질문\n" + QUESTION, "## 힌트 답변\n" + HINT_CONTENT, "- Tom → 톰 (정원사)",
                        "새 힌트가 필요하다고", "힌트 답변에 없는 새 단서")
                .doesNotContain("{{", "정답 참고", "범인: ", ClocktowerCaseFixture.TRICK_SUMMARY, "수사 기록", FOLLOWUP_QUESTION);
    }

    private static HintContext hintContext(
            List<String> inspectedEvidenceList,
            List<HintContext.NpcConversation> conversationList,
            List<HintContext.PreviousHint> previousHintList
    ) {
        HintContext.CaseInfo caseInfo = new HintContext.CaseInfo(
                ClocktowerCaseFixture.DISPLAY_TITLE,
                ClocktowerCaseFixture.INTRODUCTION,
                ClocktowerCaseFixture.INVESTIGATION_GOAL,
                ClocktowerCaseFixture.BACKGROUND_KNOWLEDGE,
                ClocktowerCaseFixture.GLOSSARY,
                List.of(new HintContext.SuspectInfo("해리스", "저택의 집사"),
                        new HintContext.SuspectInfo("마거릿", "저택의 가정부"),
                        new HintContext.SuspectInfo("엘리스", null)),
                List.of(new HintContext.EvidenceInfo("서재의 작은 상자", "책장 뒤에서 발견된 손바닥만 한 나무 상자."),
                        new HintContext.EvidenceInfo("찢긴 메모", "'9시 15분'이라는 글자만 알아볼 수 있다.")),
                "해리스",
                ClocktowerCaseFixture.TRICK_SUMMARY,
                List.of(ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION)
        );
        return new HintContext("req-1", caseInfo, inspectedEvidenceList, conversationList, previousHintList, QUESTION);
    }

    private static LlmProperties llmProperties() {
        return new LlmProperties(LlmProvider.FAKE, null, null, null, Duration.ofSeconds(60), Duration.ofSeconds(300),
                Duration.ofSeconds(60), Duration.ofSeconds(30), 150000, MAX_OUTPUT_TOKENS);
    }
}
