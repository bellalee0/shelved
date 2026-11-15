package com.shelved.infra.llm;

/**
 * 입력 토큰 수 보수적 추정 (호출 전 한도 검사용). 실제보다 적게 세어 한도를 넘는 입력을 보내는 일이 없도록 넉넉하게 센다
 */
public final class LlmTokenEstimator {

    // 메시지·시스템 프롬프트마다 붙는 역할 표시 등의 여유분
    private static final int PER_BLOCK_OVERHEAD = 10;

    private LlmTokenEstimator() {
    }

    public static int estimate(LlmRequest request) {
        long total = estimateText(request.systemPrompt()) + PER_BLOCK_OVERHEAD;
        for (LlmMessage message : request.messageList()) {
            total += estimateText(message.content()) + PER_BLOCK_OVERHEAD;
        }
        return (int) Math.min(Integer.MAX_VALUE, total);
    }

    // 추정식: ASCII 3글자당 1토큰(영문 평균 약 4글자보다 넉넉), 그 외 문자(한글 등)는 1글자당 1토큰
    static long estimateText(String text) {
        if (text == null || text.isEmpty()) {
            return 0;
        }
        long asciiCount = text.codePoints().filter(cp -> cp < 128).count();
        long otherCount = text.codePoints().count() - asciiCount;
        return (asciiCount + 2) / 3 + otherCount;
    }
}
