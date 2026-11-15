package com.shelved.infra.llm;

/**
 * LLM 호출 클라이언트. 구현은 shelved.llm.provider로 고른다 (fake 기본 / claude).
 * 도메인 서비스는 이 인터페이스를 직접 쓰지 않고, 사용량을 기록하는 LlmUsageRecordingClient를 통해 호출한다.
 * 실패는 LlmException(LlmErrorType)으로 던진다. DB 트랜잭션 안에서 호출하지 않는다 (backend.md 13)
 */
public interface LlmClient {

    LlmResponse call(LlmRequest request);

    /**
     * 모델 종류에 해당하는 모델 식별자 (사용량 로그 기록용)
     */
    String modelOf(LlmModelTier modelTier);
}
