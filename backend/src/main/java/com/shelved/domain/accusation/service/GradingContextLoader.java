package com.shelved.domain.accusation.service;

import static com.shelved.common.exception.ErrorCode.SUSPECT_NOT_FOUND;

import com.shelved.common.entity.CaseTrickElement;
import com.shelved.common.entity.Evidence;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.Suspect;
import com.shelved.common.entity.json.GradingRubric;
import com.shelved.common.entity.json.RubricItem;
import com.shelved.common.enums.Difficulty;
import com.shelved.common.exception.CustomException;
import com.shelved.domain.cases.repository.CaseTrickElementRepository;
import com.shelved.domain.cases.repository.EvidenceRepository;
import com.shelved.domain.cases.repository.SuspectRepository;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * 지목 채점 컨텍스트(GradingContext) 구성. AccusationHandler.prepare(접수 트랜잭션, rooms 행 잠금 안)에서 호출한다.
 *
 * <ul>
 *     <li>사건 단위로 묶어 조회한다: 인물 목록 1회 + 핵심 요소 목록 1회 + (쉬움일 때만) 물증 목록 1회. 요소·항목 수와 무관하다 (N+1 없음)</li>
 *     <li>범인 일치는 지목한 인물의 범인 플래그(is_culprit)로 계산하고, 사건의 culprit_suspect_id와 어긋나면 시드 오류로 실패한다</li>
 *     <li>이 사건의 인물이 아니면 404 SUSPECT_NOT_FOUND (호출 전에 걸러지지만 방어로 다시 확인한다)</li>
 *     <li>핵심 요소가 0개이거나 필수 항목이 0개인 요소가 있으면 IllegalStateException.
 *     "항목 0개 = 전부 충족"으로 처리되어 트릭 설명 없이 정답이 되는 일을 막는다</li>
 *     <li>쉬움 피드백 참고의 물증·인물 ID가 이 사건에 없으면 IllegalStateException. 시드·확정 데이터 오류이므로
 *     조용히 건너뛰어 안내가 빠진 피드백을 내보내지 않고 채점 실패로 드러낸다</li>
 * </ul>
 * 트랜잭션 안에서 호출해야 한다 (사건·인물 지연 로딩)
 */
@Component
@RequiredArgsConstructor
public class GradingContextLoader {

    private final SuspectRepository suspectRepository;
    private final EvidenceRepository evidenceRepository;
    private final CaseTrickElementRepository caseTrickElementRepository;

    public GradingContext load(Room room, Long suspectId, String trickExplanation) {

        MysteryCase mysteryCase = room.getMysteryCase();
        Long caseId = mysteryCase.getId();
        Difficulty difficulty = mysteryCase.getDifficulty();
        if (difficulty == null) {
            throw new IllegalStateException("난이도가 없는 사건입니다. caseId=" + caseId);
        }

        List<Suspect> suspectList = suspectRepository.findAllByMysteryCaseIdOrderByIdAsc(caseId);
        Suspect accused = suspectList.stream()
                .filter(suspect -> Objects.equals(suspect.getId(), suspectId))
                .findFirst()
                .orElseThrow(() -> new CustomException(SUSPECT_NOT_FOUND));
        boolean culpritCorrect = culpritCorrectOf(mysteryCase, accused);

        List<CaseTrickElement> trickElementList = caseTrickElementRepository.findAllByMysteryCaseIdOrderByOrderIndexAsc(caseId);
        if (trickElementList.isEmpty()) {
            throw new IllegalStateException("핵심 요소가 없는 사건은 채점할 수 없습니다. caseId=" + caseId);
        }

        Map<Long, String> suspectNameById = nameMap(suspectList, Suspect::getId, Suspect::getName);
        Map<Long, String> evidenceNameById = difficulty == Difficulty.EASY
                ? nameMap(evidenceRepository.findAllByMysteryCaseIdOrderByIdAsc(caseId), Evidence::getId, Evidence::getName)
                : Map.of();

        List<GradingContext.Element> elementList = trickElementList.stream()
                .map(element -> elementOf(element, difficulty, suspectNameById, evidenceNameById))
                .toList();

        return new GradingContext(
                difficulty,
                accused.getId(),
                accused.getName(),
                culpritCorrect,
                elementList,
                mysteryCase.getGlossary(),
                trickExplanation
        );
    }

    // 지목한 인물의 범인 플래그. 사건의 culprit_suspect_id가 있으면 같은 판단인지 확인한다
    private static boolean culpritCorrectOf(MysteryCase mysteryCase, Suspect accused) {
        boolean culpritCorrect = accused.isCulprit();
        Suspect culprit = mysteryCase.getCulpritSuspect();
        if (culprit != null && Objects.equals(culprit.getId(), accused.getId()) != culpritCorrect) {
            throw new IllegalStateException("범인 플래그와 사건의 범인 지정이 다릅니다. caseId=" + mysteryCase.getId());
        }
        return culpritCorrect;
    }

    private static GradingContext.Element elementOf(
            CaseTrickElement element,
            Difficulty difficulty,
            Map<Long, String> suspectNameById,
            Map<Long, String> evidenceNameById
    ) {
        GradingRubric rubric = element.getGradingRubric();
        List<RubricItem> rubricItemList = rubric == null || rubric.items() == null ? List.of() : rubric.items();
        if (rubricItemList.isEmpty()) {
            throw new IllegalStateException("필수 판정 항목이 없는 핵심 요소입니다. trickElementId=" + element.getId());
        }

        List<GradingContext.Item> itemList = rubricItemList.stream()
                .map(item -> new GradingContext.Item(
                        item.itemId(), item.label(), item.criterion(), item.acceptExamples(), item.rejectExamples()))
                .toList();

        GradingContext.EasyGuide easyGuide = difficulty == Difficulty.EASY
                ? easyGuideOf(element.getId(), rubric.easyFeedback(), suspectNameById, evidenceNameById)
                : null;

        return new GradingContext.Element(element.getId(), element.getDescription(), itemList, easyGuide);
    }

    private static GradingContext.EasyGuide easyGuideOf(
            Long trickElementId,
            GradingRubric.EasyFeedback easyFeedback,
            Map<Long, String> suspectNameById,
            Map<Long, String> evidenceNameById
    ) {
        if (easyFeedback == null) {
            return null;
        }
        return new GradingContext.EasyGuide(
                easyFeedback.guideQuestion(),
                namesOf(easyFeedback.evidenceIds(), evidenceNameById, "물증", trickElementId),
                namesOf(easyFeedback.suspectIds(), suspectNameById, "인물", trickElementId)
        );
    }

    private static List<String> namesOf(List<Long> idList, Map<Long, String> nameById, String label, Long trickElementId) {
        if (idList == null) {
            return List.of();
        }
        return idList.stream()
                .map(id -> {
                    String name = nameById.get(id);
                    if (name == null) {
                        throw new IllegalStateException(
                                "쉬움 피드백 참고의 " + label + "이 이 사건에 없습니다. trickElementId=" + trickElementId + " id=" + id);
                    }
                    return name;
                })
                .toList();
    }

    private static <T> Map<Long, String> nameMap(List<T> list, Function<T, Long> idOf, Function<T, String> nameOf) {
        return list.stream().collect(Collectors.toMap(idOf, nameOf));
    }
}
