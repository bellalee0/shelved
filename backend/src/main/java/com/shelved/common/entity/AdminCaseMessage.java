package com.shelved.common.entity;

import com.shelved.common.entity.json.InputInfo;
import com.shelved.common.entity.json.ToolChange;
import com.shelved.common.enums.AdminMessageSenderType;
import com.shelved.common.enums.AiRequestStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.util.List;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * 관리자 제작 채팅 발화. 한 요청의 관리자·AI 발화는 같은 request_id를 공유하므로
 * request_id 단독이 아닌 (case_id, request_id, sender_type)에 UNIQUE를 건다.
 */
@Entity
@Table(
        name = "admin_case_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_admin_case_messages_case_id_request_id_sender_type",
                columnNames = {"case_id", "request_id", "sender_type"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AdminCaseMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "case_id", nullable = false)
    private MysteryCase mysteryCase;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private AdminMessageSenderType senderType;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    // AI 발화의 초안 변경 요약. 관리자 발화는 빈 목록
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "tool_changes", nullable = false, columnDefinition = "jsonb")
    private List<ToolChange> toolChanges;

    // AI 발화에만 있다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_info", columnDefinition = "jsonb")
    private InputInfo inputInfo;

    // 최초 접수 시점의 CASES.chat_context_revision. 실패 요청 재시도 가능 여부 판단에 쓴다
    @Column(name = "chat_context_revision", nullable = false)
    private long chatContextRevision;

    // 관리자 발화에 보존하는 AdminChatResponse 스냅샷. 응답 DTO가 아직 없고 그대로 다시 돌려주기만 하므로
    // 전용 record 대신 JSON 트리로 둔다. 메시지 DTO에는 노출하지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "response_json", columnDefinition = "jsonb")
    private JsonNode responseJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiRequestStatus status;

    @Column(name = "error_message", columnDefinition = "text")
    private String errorMessage;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    private AdminCaseMessage(
            MysteryCase mysteryCase,
            String requestId,
            AdminMessageSenderType senderType,
            String content,
            List<ToolChange> toolChanges,
            InputInfo inputInfo,
            long chatContextRevision,
            AiRequestStatus status,
            LocalDateTime createdAt
    ) {
        this.mysteryCase = mysteryCase;
        this.requestId = requestId;
        this.senderType = senderType;
        this.content = content;
        this.toolChanges = toolChanges;
        this.inputInfo = inputInfo;
        this.chatContextRevision = chatContextRevision;
        this.status = status;
        this.createdAt = createdAt;
    }

    /**
     * 접수한 관리자 발화 (PROCESSING)
     */
    public static AdminCaseMessage ofAdmin(
            MysteryCase mysteryCase,
            String requestId,
            String content,
            long chatContextRevision,
            LocalDateTime createdAt
    ) {
        return new AdminCaseMessage(mysteryCase, requestId, AdminMessageSenderType.ADMIN, content,
                List.of(), null, chatContextRevision, AiRequestStatus.PROCESSING, createdAt);
    }

    /**
     * 정상 완료된 AI 발화 (SUCCEEDED)
     */
    public static AdminCaseMessage ofAi(
            MysteryCase mysteryCase,
            String requestId,
            String content,
            List<ToolChange> toolChanges,
            InputInfo inputInfo,
            long chatContextRevision,
            LocalDateTime createdAt
    ) {
        return new AdminCaseMessage(mysteryCase, requestId, AdminMessageSenderType.AI, content,
                toolChanges, inputInfo, chatContextRevision, AiRequestStatus.SUCCEEDED, createdAt);
    }

    public void markSucceeded(JsonNode responseJson) {
        this.status = AiRequestStatus.SUCCEEDED;
        this.responseJson = responseJson;
        this.errorMessage = null;
    }

    public void markFailed(String errorMessage) {
        this.status = AiRequestStatus.FAILED;
        this.errorMessage = errorMessage;
    }
}
