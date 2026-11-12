package com.shelved.infra.llm;

/**
 * 대화 메시지 1개. 내용은 로그에 남기지 않는다
 */
public record LlmMessage(
        Role role,
        String content
) {

    public static LlmMessage user(String content) {
        return new LlmMessage(Role.USER, content);
    }

    public static LlmMessage assistant(String content) {
        return new LlmMessage(Role.ASSISTANT, content);
    }

    // 메시지 내용이 로그·예외 메시지에 섞이지 않도록 record 기본 toString을 대체한다
    @Override
    public String toString() {
        return "LlmMessage[role=" + role + ", length=" + (content == null ? 0 : content.length()) + "]";
    }

    public enum Role {
        USER,
        ASSISTANT
    }
}
