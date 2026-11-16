package com.shelved.domain.npc.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.config.LlmProperties;
import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.LlmProvider;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.domain.npc.service.NpcAskContext.HistoryMessage;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.infra.llm.LlmMessage;
import com.shelved.infra.llm.LlmModelTier;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.PromptTemplateLoader;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.core.io.DefaultResourceLoader;

/**
 * NPC 답변 요청 구성. 가상 사건 「시계탑 저택의 마지막 종소리」만 쓴다
 */
class NpcPromptBuilderTest {

    private static final int MAX_OUTPUT_TOKENS = 777;
    private static final String PERSONA_PROMPT = "### 말투\n정중하고 짧게 말한다\n### 기본 진술\n- 9시에는 주방에서 은식기를 닦고 있었다";
    private static final String QUESTION = "어젯밤 9시에 서재 근처에 있었나요?";

    private final PromptTemplateLoader promptTemplateLoader = new PromptTemplateLoader(new DefaultResourceLoader());
    private final NpcPromptBuilder builder = new NpcPromptBuilder(promptTemplateLoader, llmProperties());

    @Test
    void build_시스템프롬프트_공통원칙과인물이름과용어집과persona() {

        // given
        NpcAskContext context = context(Difficulty.EASY, ClocktowerCaseFixture.GLOSSARY, List.of());

        // when
        LlmRequest request = builder.build(context);

        // then
        assertThat(request.requestType()).isEqualTo(LlmRequestType.NPC_CHAT);
        assertThat(request.modelTier()).isEqualTo(LlmModelTier.MAIN);
        assertThat(request.maxOutputTokens()).isEqualTo(MAX_OUTPUT_TOKENS);
        assertThat(request.systemPrompt())
                .contains("\"해리스\"")
                .contains("거짓말·숨기는 사실·침묵 주제의 범위는 응답 방식과 관계없이 그대로 지킨다")
                .contains("- Harris → 해리스 (집사)", "- Clocktower Hall → 시계탑 저택 (지명)")
                .contains(PERSONA_PROMPT)
                .doesNotContain("{{");
    }

    @ParameterizedTest
    @EnumSource(Difficulty.class)
    void build_난이도별응답방식조각_해당난이도만포함(Difficulty difficulty) {

        // given
        NpcAskContext context = context(difficulty, List.of(), List.of());
        String easy = promptTemplateLoader.load("npc-difficulty-easy.txt").strip();
        String normal = promptTemplateLoader.load("npc-difficulty-normal.txt").strip();
        String hard = promptTemplateLoader.load("npc-difficulty-hard.txt").strip();

        // when
        String systemPrompt = builder.build(context).systemPrompt();

        // then
        assertThat(systemPrompt.contains(easy)).isEqualTo(difficulty == Difficulty.EASY);
        assertThat(systemPrompt.contains(normal)).isEqualTo(difficulty == Difficulty.NORMAL);
        assertThat(systemPrompt.contains(hard)).isEqualTo(difficulty == Difficulty.HARD);
    }

    @Test
    void build_용어집없음_없음표시() {

        // given
        NpcAskContext context = context(Difficulty.NORMAL, null, List.of());

        // when
        String systemPrompt = builder.build(context).systemPrompt();

        // then
        assertThat(systemPrompt).contains("## 고유명사 표기", "(없음)");
    }

    @Test
    void build_대화이력_순서대로교대하고이번질문이마지막() {

        // given
        List<HistoryMessage> historyList = List.of(
                new HistoryMessage(MessageSenderType.USER, "종소리는 몇 번 울렸나요?"),
                new HistoryMessage(MessageSenderType.NPC, "아홉 번이었습니다."),
                new HistoryMessage(MessageSenderType.USER, "그때 어디 계셨죠?"),
                new HistoryMessage(MessageSenderType.NPC, "주방에 있었습니다."));
        NpcAskContext context = context(Difficulty.HARD, List.of(), historyList);

        // when
        List<LlmMessage> messageList = builder.build(context).messageList();

        // then
        assertThat(messageList).containsExactly(
                LlmMessage.user("종소리는 몇 번 울렸나요?"),
                LlmMessage.assistant("아홉 번이었습니다."),
                LlmMessage.user("그때 어디 계셨죠?"),
                LlmMessage.assistant("주방에 있었습니다."),
                LlmMessage.user(QUESTION));
    }

    @Test
    void build_이력이NPC발화로시작_user로시작하도록앞의NPC발화제외() {

        // given: 한도로 잘린 이력이 답변부터 시작하는 경우
        List<HistoryMessage> historyList = List.of(
                new HistoryMessage(MessageSenderType.NPC, "아홉 번이었습니다."),
                new HistoryMessage(MessageSenderType.USER, "그때 어디 계셨죠?"),
                new HistoryMessage(MessageSenderType.NPC, "주방에 있었습니다."));
        NpcAskContext context = context(Difficulty.HARD, List.of(), historyList);

        // when
        List<LlmMessage> messageList = builder.build(context).messageList();

        // then
        assertThat(messageList).extracting(LlmMessage::role).containsExactly(
                LlmMessage.Role.USER, LlmMessage.Role.ASSISTANT, LlmMessage.Role.USER);
        assertThat(messageList.getFirst().content()).isEqualTo("그때 어디 계셨죠?");
    }

    @Test
    void contextToString_설정과질문미포함() {

        // given
        NpcAskContext context = context(Difficulty.EASY, ClocktowerCaseFixture.GLOSSARY,
                List.of(new HistoryMessage(MessageSenderType.USER, "종소리는 몇 번 울렸나요?")));

        // when
        String text = context.toString();

        // then
        assertThat(text).doesNotContain("정중하고", QUESTION, "종소리", "해리스");
    }

    private static NpcAskContext context(Difficulty difficulty, List<GlossaryTerm> glossary, List<HistoryMessage> historyList) {
        return new NpcAskContext("5f0e8a4e-2b7c-4d0e-9a31-2f1f7f0c9c11", 1L, "해리스", PERSONA_PROMPT,
                difficulty, glossary, historyList, QUESTION);
    }

    private static LlmProperties llmProperties() {
        return new LlmProperties(LlmProvider.FAKE, null, null, null, Duration.ofSeconds(60), Duration.ofSeconds(300),
                Duration.ofSeconds(60), Duration.ofSeconds(30), 150000, MAX_OUTPUT_TOKENS);
    }
}
