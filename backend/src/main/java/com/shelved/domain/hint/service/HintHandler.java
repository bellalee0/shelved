package com.shelved.domain.hint.service;

import static com.shelved.common.exception.ErrorCode.HINT_LIMIT_EXCEEDED;

import com.shelved.common.config.HintProperties;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.CaseTrickElement;
import com.shelved.common.entity.Hint;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.MessageSenderType;
import com.shelved.common.exception.CustomException;
import com.shelved.domain.airequest.service.AiRequestCommand;
import com.shelved.domain.airequest.service.AiRequestHandler;
import com.shelved.domain.airequest.service.AiRequestSuccess;
import com.shelved.domain.cases.repository.CaseTrickElementRepository;
import com.shelved.domain.cases.repository.EvidenceRepository;
import com.shelved.domain.cases.repository.SuspectRepository;
import com.shelved.domain.evidence.repository.EvidenceInspectionRepository;
import com.shelved.domain.hint.dto.HintCreateResponse;
import com.shelved.domain.hint.dto.HintResponse;
import com.shelved.domain.hint.repository.HintRepository;
import com.shelved.domain.npc.repository.NpcMessageRepository;
import com.shelved.domain.npc.repository.NpcRecentMessageRow;
import com.shelved.domain.room.dto.RoomStateResponse.RoomUsageCount;
import com.shelved.infra.llm.LlmRequest;
import com.shelved.infra.llm.LlmResponse;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/**
 * 새 힌트(HINT)의 AiRequestHandler. 흐름·트랜잭션은 AiRequestExecutor가 정하고, 여기서는 힌트 고유 부분만 한다. 대상 키는 없다(세션당 동시 1건).
 *
 * <p>한도: 사용 확정(hints 행) + 처리 중 확보(PROCESSING HINT) >= 난이도별 한도이면 409 HINT_LIMIT_EXCEEDED.
 * prepare는 접수 트랜잭션에서 요청 행을 저장·재시작하기 전에 호출되므로 새 요청은 아직 행이 없고, 실패한 요청의 재시도는 자기 행이
 * 아직 FAILED라 처리 중 수에 들어가지 않는다 (이중 계산 없음). 확정 시점에도 한도를 한 번 더 확인한다.</p>
 *
 * <p>프롬프트에는 NPC별 최근 shelved.hint.npc-message-limit개 대화 원문과 최근 shelved.hint.history-limit개 이전 힌트만 넣는다(의도된 설계).
 * 수사가 길어질수록 입력 토큰·비용·응답 시간이 계속 늘어나는 것을 막고, 오래된 대화보다 최근 수사 흐름을 기준으로 방향을 잡게 하기 위함이다.
 * 한도 안의 내용으로도 입력 한도를 넘으면 더 자르지 않고 LLM_INPUT_TOO_LARGE(→ 502 HINT_GENERATION_FAILED)로 실패한다.</p>
 */
@Component
@RequiredArgsConstructor
public class HintHandler implements AiRequestHandler<HintContext> {

    private final HintRepository hintRepository;
    private final SuspectRepository suspectRepository;
    private final EvidenceRepository evidenceRepository;
    private final CaseTrickElementRepository caseTrickElementRepository;
    private final EvidenceInspectionRepository evidenceInspectionRepository;
    private final NpcMessageRepository npcMessageRepository;
    private final HintUsageCounter hintUsageCounter;
    private final HintPromptBuilder hintPromptBuilder;
    private final HintProperties hintProperties;
    private final Clock clock;

    @Override
    public AiRequestType type() {
        return AiRequestType.HINT;
    }

    /**
     * 접수 트랜잭션 안(rooms 행 잠금). 한도를 최종 확인하고 프롬프트 재료를 사건·세션 단위 조회로 읽는다
     */
    @Override
    public HintContext prepare(Room room, AiRequestCommand command) {

        MysteryCase mysteryCase = room.getMysteryCase();
        validateHintAvailable(room.getId(), mysteryCase);

        return new HintContext(
                command.requestId(),
                caseInfoOf(mysteryCase),
                evidenceInspectionRepository.findInspectedEvidenceNameList(room.getId()),
                conversationListOf(room.getId()),
                previousHintListOf(room.getId()),
                command.input().question()
        );
    }

    @Override
    public LlmRequest buildLlmRequest(HintContext context) {
        return hintPromptBuilder.buildHint(context);
    }

    /**
     * 확정 트랜잭션 안(rooms 행 잠금). 답변을 검증하고, 한도를 한 번 더 확인한 뒤 힌트를 저장한다.
     * 응답의 hintCount는 이 힌트의 사용을 반영한 값이다 (자기 요청은 아직 PROCESSING이라 확보분에서 뺀다)
     */
    @Override
    public AiRequestSuccess complete(Room room, AiRequest aiRequest, HintContext context, LlmResponse response) {

        String content = HintAnswerValidator.validate(response.text(), "힌트");
        MysteryCase mysteryCase = room.getMysteryCase();
        RoomUsageCount before = hintUsageCounter.countHintUsage(room.getId(), mysteryCase.getDifficulty());
        // 임시 확보 설계상 넘을 수 없지만, 위반이면 저장하지 않는다 (요청은 실패로 정리된다)
        if (before.used() >= before.limit()) {
            throw new CustomException(HINT_LIMIT_EXCEEDED);
        }

        Hint hint = hintRepository.saveAndFlush(new Hint(
                room, room.getHostUser(), context.requestId(), context.question(), content, LocalDateTime.now(clock)));

        RoomUsageCount after = new RoomUsageCount(before.limit(), before.used() + 1L, Math.max(0, before.reserved() - 1));
        HintCreateResponse createResponse = new HintCreateResponse(new HintResponse(hint, null, true), after);

        return new AiRequestSuccess(hint.getId(), createResponse);
    }

    private void validateHintAvailable(Long roomId, MysteryCase mysteryCase) {
        RoomUsageCount hintCount = hintUsageCounter.countHintUsage(roomId, mysteryCase.getDifficulty());
        if (HintUsageCounter.isExhausted(hintCount)) {
            throw new CustomException(HINT_LIMIT_EXCEEDED);
        }
    }

    // 범인은 이름으로만 넣는다 (내부 ID·플래그 이름은 프롬프트에 쓰지 않는다)
    private HintContext.CaseInfo caseInfoOf(MysteryCase mysteryCase) {
        Long caseId = mysteryCase.getId();
        List<Suspect> suspectList = suspectRepository.findAllByMysteryCaseIdOrderByIdAsc(caseId);
        Long culpritId = mysteryCase.getCulpritSuspect() == null ? null : mysteryCase.getCulpritSuspect().getId();
        String culpritName = suspectList.stream()
                .filter(suspect -> Objects.equals(suspect.getId(), culpritId))
                .map(Suspect::getName)
                .findFirst()
                .orElse(null);

        return new HintContext.CaseInfo(
                mysteryCase.getDisplayTitle(),
                mysteryCase.getIntroduction(),
                mysteryCase.getInvestigationGoal(),
                mysteryCase.getBackgroundKnowledge(),
                mysteryCase.getGlossary(),
                suspectList.stream()
                        .map(suspect -> new HintContext.SuspectInfo(suspect.getName(), suspect.getPublicIntro()))
                        .toList(),
                evidenceRepository.findAllByMysteryCaseIdOrderByIdAsc(caseId).stream()
                        .map(evidence -> new HintContext.EvidenceInfo(evidence.getName(), evidence.getDescription()))
                        .toList(),
                culpritName,
                mysteryCase.getTrickSummary(),
                caseTrickElementRepository.findAllByMysteryCaseIdOrderByOrderIndexAsc(caseId).stream()
                        .map(CaseTrickElement::getDescription)
                        .toList()
        );
    }

    private List<HintContext.NpcConversation> conversationListOf(Long roomId) {
        List<NpcRecentMessageRow> rowList = npcMessageRepository.findRecentMessageRowList(
                roomId, hintProperties.npcMessageLimit());

        Map<Long, List<NpcRecentMessageRow>> rowsBySuspect = new LinkedHashMap<>();
        for (NpcRecentMessageRow row : rowList) {
            rowsBySuspect.computeIfAbsent(row.suspectId(), suspectId -> new ArrayList<>()).add(row);
        }

        return rowsBySuspect.values().stream()
                .map(rows -> new HintContext.NpcConversation(
                        rows.getFirst().suspectName(),
                        rows.stream()
                                .map(row -> new HintContext.Message(row.senderType() == MessageSenderType.USER, row.content()))
                                .toList()))
                .toList();
    }

    private List<HintContext.PreviousHint> previousHintListOf(Long roomId) {
        List<Hint> recentFirst = hintRepository.findByRoomIdOrderByCreatedAtDescIdDesc(
                roomId, Limit.of(hintProperties.historyLimit()));
        return recentFirst.reversed().stream()
                .map(hint -> new HintContext.PreviousHint(hint.getQuestion(), hint.getContent()))
                .toList();
    }
}
