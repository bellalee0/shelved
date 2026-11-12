package com.shelved.common.entity;

import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestStatus;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.exception.ErrorCode;
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
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import tools.jackson.databind.JsonNode;

/**
 * 게임플레이 AI 요청의 접수·처리 상태. 같은 세션의 같은 requestId는 한 행이다.
 */
@Entity
@Table(
        name = "ai_requests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_ai_requests_room_id_request_id",
                columnNames = {"room_id", "request_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AiRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "request_id", nullable = false)
    private String requestId;

    @Enumerated(EnumType.STRING)
    @Column(name = "request_type", nullable = false)
    private AiRequestType requestType;

    // NPC_ASK: suspectId, HINT_FOLLOWUP: hintId, 그 외 null
    @Column(name = "target_id")
    private Long targetId;

    // 플레이어가 보낸 입력만 저장한다 (내부 프롬프트 제외). 플레이어에게 그대로 반환하지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "input_json", nullable = false, columnDefinition = "jsonb")
    private AiRequestInput inputJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AiRequestStatus status;

    // 재시도마다 1 증가. 늦게 도착한 이전 실행의 결과를 반영하지 않기 위한 기준
    @Column(name = "execution_no", nullable = false)
    private int executionNo;

    // 서버 최초 접수 시각
    @Column(name = "accepted_at", nullable = false)
    private LocalDateTime acceptedAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    // 채점 실패 후 새 답안으로 대체된 시각 (ACCUSATION)
    @Column(name = "superseded_at")
    private LocalDateTime supersededAt;

    // 성공 시 생성된 결과 행 ID (npc_messages·hints·hint_followups·accusations)
    @Column(name = "result_ref_id")
    private Long resultRefId;

    // 최초 성공 당시 원래 POST의 플레이어용 성공 응답 스냅샷. 응답 DTO가 아직 없고 유형별로 구조가 달라
    // 전용 record 대신 JSON 트리로 둔다. 내부 프롬프트는 넣지 않는다
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "result_json", columnDefinition = "jsonb")
    private JsonNode resultJson;

    // 실패 시 ErrorCode 이름. 코드가 늘어나도 기존 행에 영향이 없도록 문자열로 저장한다
    @Column(name = "error_code")
    private String errorCode;

    /**
     * 처리 중(PROCESSING) 요청 접수. 실행 번호는 1부터
     */
    public AiRequest(
            Room room,
            String requestId,
            AiRequestType requestType,
            Long targetId,
            AiRequestInput inputJson,
            LocalDateTime acceptedAt
    ) {
        this.room = room;
        this.requestId = requestId;
        this.requestType = requestType;
        this.targetId = targetId;
        this.inputJson = inputJson;
        this.status = AiRequestStatus.PROCESSING;
        this.executionNo = 1;
        this.acceptedAt = acceptedAt;
        this.updatedAt = acceptedAt;
    }

    /**
     * 같은 requestId로 다시 받은 요청이 처음 요청과 같은지 (유형·대상·플레이어 입력)
     */
    public boolean isSameRequest(AiRequestType requestType, Long targetId, AiRequestInput inputJson) {
        return this.requestType == requestType
                && Objects.equals(this.targetId, targetId)
                && Objects.equals(this.inputJson, inputJson);
    }

    /**
     * 이 실행 번호의 결과를 아직 반영할 수 있는지 (최신 실행이고 처리 중). 아니면 늦은 응답이다
     */
    public boolean isCurrentExecution(int executionNo) {
        return this.status == AiRequestStatus.PROCESSING && this.executionNo == executionNo;
    }

    /**
     * 실패한 요청을 같은 행에서 다시 실행한다. 실행 번호를 1 올려 이전 실행의 늦은 응답을 구분한다
     */
    public void restart(LocalDateTime updatedAt) {
        this.status = AiRequestStatus.PROCESSING;
        this.executionNo++;
        this.errorCode = null;
        this.updatedAt = updatedAt;
    }

    public void succeed(Long resultRefId, JsonNode resultJson, LocalDateTime updatedAt) {
        this.status = AiRequestStatus.SUCCEEDED;
        this.resultRefId = resultRefId;
        this.resultJson = resultJson;
        this.errorCode = null;
        this.updatedAt = updatedAt;
    }

    public void fail(ErrorCode errorCode, LocalDateTime updatedAt) {
        this.status = AiRequestStatus.FAILED;
        this.errorCode = errorCode.name();
        this.updatedAt = updatedAt;
    }
}
