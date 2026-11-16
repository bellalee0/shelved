package com.shelved.domain.hint.service;

import com.shelved.domain.airequest.service.AiResponseInvalidException;

/**
 * 힌트·보충 질문 답변의 형식 검증. 잘못되면 AiResponseInvalidException (AI 호출 실패로 처리, 차감 없음)
 */
final class HintAnswerValidator {

    // 3~6문장 답변에 비해 충분히 큰 상한. 넘으면 형식 오류로 본다
    static final int ANSWER_MAX_LENGTH = 2000;

    private HintAnswerValidator() {
    }

    /**
     * 비었거나 공백뿐이거나 ANSWER_MAX_LENGTH를 넘으면 형식 오류. 앞뒤 공백은 제거해 돌려준다
     *
     * @param label 예외 사유에 쓸 구분 (응답 본문은 넣지 않는다)
     */
    static String validate(String text, String label) {
        if (text == null || text.isBlank()) {
            throw new AiResponseInvalidException(label + " 답변이 비어 있음");
        }
        String answer = text.strip();
        if (answer.length() > ANSWER_MAX_LENGTH) {
            throw new AiResponseInvalidException(label + " 답변이 너무 김 length=" + answer.length());
        }
        return answer;
    }
}
