package com.shelved.domain.accusation.service;

import com.shelved.common.entity.json.GlossaryTerm;
import com.shelved.common.enums.Difficulty;
import java.util.List;
import java.util.Objects;

/**
 * 지목 채점 1건의 AI 호출 컨텍스트 (불변). 접수 트랜잭션에서 GradingContextLoader가 만들고, 이후에는 DB를 읽지 않는다.
 * 채점 기준·답안이 담기므로 응답·로그에 넣지 않는다 (toString은 개수만)
 *
 * @param difficulty       사건 난이도 (피드백 상세도)
 * @param suspectId        지목한 인물 ID
 * @param suspectName      지목한 인물 이름 (응답 DTO용. 프롬프트에는 넣지 않는다)
 * @param culpritCorrect   지목한 인물이 범인인지 (서버가 범인 플래그로 계산)
 * @param elementList      핵심 요소 (order_index 순, 1개 이상)
 * @param glossary         사건 용어집 (표기 일관성용)
 * @param trickExplanation 제출한 트릭 설명 원문
 */
public record GradingContext(
        Difficulty difficulty,
        Long suspectId,
        String suspectName,
        boolean culpritCorrect,
        List<Element> elementList,
        List<GlossaryTerm> glossary,
        String trickExplanation
) {

    public GradingContext {
        Objects.requireNonNull(difficulty, "difficulty");
        Objects.requireNonNull(trickExplanation, "trickExplanation");
        elementList = List.copyOf(elementList);
        if (elementList.isEmpty()) {
            throw new IllegalStateException("채점할 핵심 요소가 없습니다.");
        }
        glossary = glossary == null ? List.of() : List.copyOf(glossary);
    }

    /**
     * 모든 요소의 모든 필수 항목 (요소 순 → 항목 순). 응답 검증의 기대 항목 집합이다
     */
    public List<ItemKey> expectedItemKeyList() {
        return elementList.stream()
                .flatMap(element -> element.itemList().stream()
                        .map(item -> new ItemKey(element.trickElementId(), item.itemId())))
                .toList();
    }

    /**
     * @param easyGuide 쉬움 난이도일 때만 채운다. 그 밖의 난이도이거나 사건에 쉬움 피드백 참고가 없으면 null
     */
    public record Element(
            Long trickElementId,
            String description,
            List<Item> itemList,
            EasyGuide easyGuide
    ) {

        public Element {
            Objects.requireNonNull(trickElementId, "trickElementId");
            itemList = List.copyOf(itemList);
            if (itemList.isEmpty()) {
                throw new IllegalStateException("필수 판정 항목이 없는 핵심 요소입니다. trickElementId=" + trickElementId);
            }
        }

        @Override
        public String toString() {
            return "Element[trickElementId=" + trickElementId + ", itemCount=" + itemList.size()
                    + ", easyGuide=" + (easyGuide != null) + "]";
        }
    }

    public record Item(
            String itemId,
            String label,
            String criterion,
            List<String> acceptExampleList,
            List<String> rejectExampleList
    ) {

        public Item {
            Objects.requireNonNull(itemId, "itemId");
            acceptExampleList = acceptExampleList == null ? List.of() : List.copyOf(acceptExampleList);
            rejectExampleList = rejectExampleList == null ? List.of() : List.copyOf(rejectExampleList);
        }

        @Override
        public String toString() {
            return "Item[itemId=" + itemId + "]";
        }
    }

    /**
     * 쉬움 피드백 참고. 물증·인물은 ID가 아닌 이름으로 바꿔 둔다
     */
    public record EasyGuide(
            String guideQuestion,
            List<String> evidenceNameList,
            List<String> suspectNameList
    ) {

        public EasyGuide {
            evidenceNameList = List.copyOf(evidenceNameList);
            suspectNameList = List.copyOf(suspectNameList);
        }

        @Override
        public String toString() {
            return "EasyGuide";
        }
    }

    /**
     * 판정 항목 식별자 (요소 ID + 요소 안의 항목 ID)
     */
    public record ItemKey(
            Long trickElementId,
            String itemId
    ) {
    }

    // 채점 기준·답안이 로그에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "GradingContext[difficulty=" + difficulty
                + ", suspectId=" + suspectId
                + ", elementCount=" + elementList.size()
                + ", answerLength=" + trickExplanation.length() + "]";
    }
}
