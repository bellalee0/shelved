package com.shelved.common.entity;

import com.shelved.common.enums.MessageSenderType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "npc_messages",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_npc_messages_conversation_id_sequence_no",
                columnNames = {"conversation_id", "sequence_no"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NpcMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "conversation_id", nullable = false)
    private NpcConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(name = "sender_type", nullable = false)
    private MessageSenderType senderType;

    // NPC 발화면 null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_user_id")
    private User senderUser;

    @Column(name = "content", nullable = false, columnDefinition = "text")
    private String content;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    // 대화 안의 순번 (정렬 기준)
    @Column(name = "sequence_no", nullable = false)
    private int sequenceNo;

    public NpcMessage(
            NpcConversation conversation,
            MessageSenderType senderType,
            User senderUser,
            String content,
            int sequenceNo,
            LocalDateTime createdAt
    ) {
        this.conversation = conversation;
        this.senderType = senderType;
        this.senderUser = senderUser;
        this.content = content;
        this.sequenceNo = sequenceNo;
        this.createdAt = createdAt;
    }
}
