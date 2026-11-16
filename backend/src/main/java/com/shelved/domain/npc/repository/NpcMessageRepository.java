package com.shelved.domain.npc.repository;

import com.shelved.common.entity.NpcMessage;
import com.shelved.domain.npc.dto.NpcConversationSummaryResponse;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NpcMessageRepository extends JpaRepository<NpcMessage, Long> {

    List<NpcMessage> findByConversationIdOrderBySequenceNoAsc(Long conversationId);

    /**
     * 최근 메시지부터 limit개 (프롬프트의 대화 이력용)
     */
    List<NpcMessage> findByConversationIdOrderBySequenceNoDesc(Long conversationId, Limit limit);

    @Query("select max(m.sequenceNo) from NpcMessage m where m.conversation.id = :conversationId")
    Optional<Integer> findMaxSequenceNo(@Param("conversationId") Long conversationId);

    /**
     * 세션 상태의 conversations: 대화 기록이 있는 NPC별 메시지 수·마지막 메시지 시각 (suspectId 오름차순, 한 번에 집계)
     */
    @Query("""
            select new com.shelved.domain.npc.dto.NpcConversationSummaryResponse(c.suspect.id, count(m), max(m.createdAt))
            from NpcMessage m join m.conversation c
            where c.room.id = :roomId
            group by c.suspect.id
            order by c.suspect.id asc
            """)
    List<NpcConversationSummaryResponse> findConversationSummaryList(@Param("roomId") Long roomId);

    /**
     * 세션의 모든 NPC 대화에서 대화별 최근 limit개 메시지 (suspectId → sequenceNo 오름차순, 한 번에 조회. 힌트 프롬프트용).
     * 같은 대화에서 자기보다 뒤인 메시지가 limit개 미만인 메시지만 고른다
     */
    @Query("""
            select new com.shelved.domain.npc.repository.NpcRecentMessageRow(
                s.id, s.name, m.senderType, m.content, m.sequenceNo)
            from NpcMessage m join m.conversation c join c.suspect s
            where c.room.id = :roomId
              and (select count(later) from NpcMessage later
                   where later.conversation = c and later.sequenceNo > m.sequenceNo) < :limit
            order by s.id asc, m.sequenceNo asc
            """)
    List<NpcRecentMessageRow> findRecentMessageRowList(@Param("roomId") Long roomId, @Param("limit") long limit);
}
