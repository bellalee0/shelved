package com.shelved.domain.hint.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.shelved.common.config.HintProperties;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.Hint;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.cases.repository.CaseTrickElementRepository;
import com.shelved.domain.cases.repository.EvidenceRepository;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.evidence.repository.EvidenceInspectionRepository;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.npc.repository.NpcMessageRepository;
import com.shelved.domain.npc.repository.NpcRecentMessageRow;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import com.shelved.infra.llm.LlmResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Limit;

/**
 * 새 힌트 핸들러의 한도 확인·컨텍스트 구성. 가상 사건만 쓴다
 */
@ExtendWith(MockitoExtension.class)
class HintHandlerTest {

    private static final Long ROOM_ID = 7L;
    private static final Long CASE_ID = 3L;
    private static final HintProperties HINT_PROPERTIES = new HintProperties(20, 10);

    @Mock
    private HintRepository hintRepository;
    @Mock
    private SuspectRepository suspectRepository;
    @Mock
    private EvidenceRepository evidenceRepository;
    @Mock
    private CaseTrickElementRepository caseTrickElementRepository;
    @Mock
    private EvidenceInspectionRepository evidenceInspectionRepository;
    @Mock
    private NpcMessageRepository npcMessageRepository;
    @Mock
    private HintUsageCounter hintUsageCounter;
    @Mock
    private HintPromptBuilder hintPromptBuilder;
    @Mock
    private Room room;
    @Mock
    private MysteryCase mysteryCase;
    @Mock
    private AiRequest aiRequest;

    private HintHandler handler;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-11-12T05:00:00Z"), ZoneId.of("Asia/Seoul"));
        handler = new HintHandler(hintRepository, suspectRepository, evidenceRepository, caseTrickElementRepository,
                evidenceInspectionRepository, npcMessageRepository, hintUsageCounter, hintPromptBuilder, HINT_PROPERTIES, clock);
        given(room.getId()).willReturn(ROOM_ID);
        given(room.getMysteryCase()).willReturn(mysteryCase);
        given(mysteryCase.getDifficulty()).willReturn(Difficulty.HARD);
    }

    @Test
    void prepare_확정과처리중합이한도이상_HINT_LIMIT_EXCEEDED_컨텍스트조회없음() {

        // given: 어려움 1회 중 처리 중 1건 확보
        given(hintUsageCounter.countHintUsage(ROOM_ID, Difficulty.HARD)).willReturn(new RoomUsageCount(1, 0L, 1L));

        // when & then
        assertThatThrownBy(() -> handler.prepare(room, command()))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.HINT_LIMIT_EXCEEDED));
        verify(npcMessageRepository, never()).findRecentMessageRowList(any(), anyLong());
    }

    @Test
    void prepare_NPC메시지와이전힌트는설정한도로조회_이전힌트오래된순_NPC별묶음() {

        // given
        given(mysteryCase.getId()).willReturn(CASE_ID);
        given(hintUsageCounter.countHintUsage(ROOM_ID, Difficulty.HARD)).willReturn(new RoomUsageCount(1, 0L, 0L));
        given(npcMessageRepository.findRecentMessageRowList(ROOM_ID, 20)).willReturn(List.of(
                new NpcRecentMessageRow(1L, "해리스", MessageSenderType.USER, "집사 질문", 1),
                new NpcRecentMessageRow(1L, "해리스", MessageSenderType.NPC, "집사 답변", 2),
                new NpcRecentMessageRow(2L, "마거릿", MessageSenderType.USER, "가정부 질문", 1)));
        Hint newer = new Hint(room, null, "r2", "둘째 질문", "둘째 답변", LocalDateTime.of(2026, 11, 12, 14, 2));
        Hint older = new Hint(room, null, "r1", "첫 질문", "첫 답변", LocalDateTime.of(2026, 11, 12, 14, 1));
        given(hintRepository.findByRoomIdOrderByCreatedAtDescIdDesc(ROOM_ID, Limit.of(10))).willReturn(List.of(newer, older));

        // when
        HintContext context = handler.prepare(room, command());

        // then
        assertThat(context.previousHintList()).extracting(HintContext.PreviousHint::question).containsExactly("첫 질문", "둘째 질문");
        assertThat(context.conversationList()).extracting(HintContext.NpcConversation::suspectName).containsExactly("해리스", "마거릿");
        assertThat(context.conversationList().getFirst().messageList())
                .containsExactly(new HintContext.Message(true, "집사 질문"), new HintContext.Message(false, "집사 답변"));
        assertThat(context.question()).isEqualTo("질문");
    }

    @Test
    void complete_확정시점에이미한도사용_저장하지않고HINT_LIMIT_EXCEEDED() {

        // given: 임시 확보 설계상 일어나지 않지만, 확정 시점에 사용 확정이 이미 한도
        given(hintUsageCounter.countHintUsage(ROOM_ID, Difficulty.HARD)).willReturn(new RoomUsageCount(1, 1L, 1L));
        HintContext context = new HintContext("req-1",
                new HintContext.CaseInfo(null, null, null, null, null, List.of(), List.of(), null, null, List.of()),
                List.of(), List.of(), List.of(), "질문");

        // when & then
        assertThatThrownBy(() -> handler.complete(room, aiRequest, context, new LlmResponse("가상 힌트", 1, 1, "fake")))
                .isInstanceOfSatisfying(CustomException.class,
                        e -> assertThat(e.getErrorCode()).isEqualTo(ErrorCode.HINT_LIMIT_EXCEEDED));
        verify(hintRepository, never()).saveAndFlush(any());
    }

    private static AiRequestCommand command() {
        return new AiRequestCommand(ROOM_ID, "req-1", null, AiRequestInput.question("질문"), "conn-1");
    }
}
