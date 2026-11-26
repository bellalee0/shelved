package com.shelved.domain.accusation.service;

import com.shelved.common.enums.Difficulty;
import com.shelved.fixture.ClocktowerCaseFixture;
import java.util.List;

/**
 * 채점 단위 테스트용 가상 사건 「시계탑 저택의 마지막 종소리」 컨텍스트 (요소 2개, 요소당 항목 2개).
 * 요소 1은 ClocktowerCaseFixture의 기준 그대로, 요소 2는 테스트용으로 지어낸 가상 기준이다.
 * 인물 ID(9001, 9002)는 프롬프트의 다른 숫자와 겹치지 않게 둔다
 */
final class GradingTestData {

    static final Long BUTLER_ID = 9001L;
    static final String BUTLER_NAME = "해리스";
    static final Long HOUSEKEEPER_ID = 9002L;
    static final String HOUSEKEEPER_NAME = "마거릿";

    static final Long ELEMENT_1_ID = 101L;
    static final Long ELEMENT_2_ID = 102L;
    static final String ELEMENT_2_DESCRIPTION = "범행 뒤 녹음기를 회수해 흔적을 없애려 했다";
    static final String ELEMENT_2_GUIDE_QUESTION = "사건 뒤에 서재를 드나든 사람은 누구였나요?";

    static final String ANSWER = "집사가 녹음기를 사용했다.";

    static final List<GradingContext.ItemKey> EXPECTED_KEYS = List.of(
            new GradingContext.ItemKey(ELEMENT_1_ID, "trk-1-i1"),
            new GradingContext.ItemKey(ELEMENT_1_ID, "trk-1-i2"),
            new GradingContext.ItemKey(ELEMENT_2_ID, "trk-2-i1"),
            new GradingContext.ItemKey(ELEMENT_2_ID, "trk-2-i2")
    );

    private GradingTestData() {
    }

    static GradingContext context(Difficulty difficulty, boolean culpritCorrect) {
        return context(difficulty, culpritCorrect, ANSWER);
    }

    static GradingContext context(Difficulty difficulty, boolean culpritCorrect, String answer) {
        boolean easy = difficulty == Difficulty.EASY;
        List<GradingContext.Item> element1Items = ClocktowerCaseFixture.RUBRIC_ITEMS.stream()
                .map(item -> new GradingContext.Item(
                        item.itemId(), item.label(), item.criterion(), item.acceptExamples(), item.rejectExamples()))
                .toList();
        GradingContext.Element element1 = new GradingContext.Element(
                ELEMENT_1_ID,
                ClocktowerCaseFixture.TRICK_ELEMENT_DESCRIPTION,
                element1Items,
                easy ? new GradingContext.EasyGuide(ClocktowerCaseFixture.EASY_GUIDE_QUESTION,
                        List.of("서재의 작은 상자"), List.of(BUTLER_NAME)) : null);
        GradingContext.Element element2 = new GradingContext.Element(
                ELEMENT_2_ID,
                ELEMENT_2_DESCRIPTION,
                List.of(
                        new GradingContext.Item("trk-2-i1", "회수 시점", "범행 뒤에 장치를 회수했음을 밝힘",
                                List.of("나중에 녹음기를 치웠다"), List.of()),
                        new GradingContext.Item("trk-2-i2", "회수 목적", "흔적을 없애기 위함을 밝힘",
                                List.of("증거를 없애려고"), List.of("고장 나서"))),
                easy ? new GradingContext.EasyGuide(ELEMENT_2_GUIDE_QUESTION,
                        List.of("찢긴 메모"), List.of(HOUSEKEEPER_NAME)) : null);

        return new GradingContext(
                difficulty,
                culpritCorrect ? BUTLER_ID : HOUSEKEEPER_ID,
                culpritCorrect ? BUTLER_NAME : HOUSEKEEPER_NAME,
                culpritCorrect,
                List.of(element1, element2),
                List.of(),
                answer
        );
    }
}
