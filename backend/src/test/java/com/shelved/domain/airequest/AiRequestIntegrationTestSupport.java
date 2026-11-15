package com.shelved.domain.airequest;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.LlmUsageLog;
import com.shelved.common.entity.MysteryCase;
import com.shelved.common.entity.Room;
import com.shelved.common.entity.RoomParticipant;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.jwt.AuthUser;
import com.shelved.common.jwt.JwtProvider;
import com.shelved.domain.room.service.RoomLifecycleService;
import com.shelved.fixture.ClocktowerCaseFixture;
import com.shelved.fixture.ClocktowerCaseFixture.SampleCase;
import com.shelved.infra.llm.FakeLlmClient;
import jakarta.persistence.EntityManager;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * AI 요청 통합 테스트 공통 준비: 가상 사건 「시계탑 저택의 마지막 종소리」, 사용자, 세션 생성·시작, Fake LLM 초기화
 */
public abstract class AiRequestIntegrationTestSupport extends IntegrationTestSupport {

    protected static final LocalDateTime T0 = LocalDateTime.of(2026, 11, 12, 14, 0);
    protected static final int LIMIT_SEC = 1200;

    @Autowired
    protected EntityManager entityManager;

    @Autowired
    protected TransactionTemplate transactionTemplate;

    @Autowired
    protected FakeLlmClient fakeLlmClient;

    @Autowired
    protected RoomLifecycleService roomLifecycleService;

    @Autowired
    protected JwtProvider jwtProvider;

    protected SampleCase sample;
    protected User user;

    @BeforeEach
    void setUpAiRequestBase() {
        testClock.fixAt(T0);
        fakeLlmClient.reset();
        sample = transactionTemplate.execute(status -> ClocktowerCaseFixture.persistPublished(entityManager, T0));
        user = saveUser();
    }

    @AfterEach
    void resetFakeLlmClient() {
        fakeLlmClient.reset();
    }

    protected User saveUser() {
        return transactionTemplate.execute(status -> {
            User saved = new User("google-" + UUID.randomUUID(), UserRole.PLAYER);
            saved.updateNickname("Nk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
            entityManager.persist(saved);
            return saved;
        });
    }

    protected Long saveReadyRoom(User owner) {
        return transactionTemplate.execute(status -> {
            User managedUser = entityManager.getReference(User.class, owner.getId());
            MysteryCase mysteryCase = entityManager.getReference(MysteryCase.class, sample.mysteryCase().getId());
            Room room = new Room(mysteryCase, managedUser, false, LIMIT_SEC, T0.minusMinutes(1));
            entityManager.persist(room);
            entityManager.persist(new RoomParticipant(room, managedUser));
            return room.getId();
        });
    }

    /**
     * 세션을 시작하고 connectionId를 돌려준다
     */
    protected String start(User owner, Long roomId) {
        return roomLifecycleService.startRoom(authUserOf(owner), roomId).connectionId();
    }

    protected AiRequest findAiRequest(Long roomId, String requestId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select a from AiRequest a where a.room.id = :roomId and a.requestId = :requestId", AiRequest.class)
                .setParameter("roomId", roomId)
                .setParameter("requestId", requestId)
                .getSingleResult());
    }

    protected Room findRoom(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.find(Room.class, roomId));
    }

    protected List<LlmUsageLog> findUsageLogList(Long roomId) {
        return transactionTemplate.execute(status -> entityManager.createQuery(
                        "select l from LlmUsageLog l join fetch l.room join fetch l.mysteryCase where l.room.id = :roomId order by l.id",
                        LlmUsageLog.class)
                .setParameter("roomId", roomId)
                .getResultList());
    }

    /**
     * 하트비트를 계속 받은 것처럼 마지막 접속 확인 시각을 옮기고 시계를 그 시각으로 맞춘다
     */
    protected void keepAliveUntil(Long roomId, LocalDateTime seenAt) {
        transactionTemplate.executeWithoutResult(status -> entityManager.createQuery(
                        "update Room r set r.lastSeenAt = :seenAt where r.id = :roomId")
                .setParameter("seenAt", seenAt)
                .setParameter("roomId", roomId)
                .executeUpdate());
        testClock.fixAt(seenAt);
    }

    protected static AuthUser authUserOf(User owner) {
        return new AuthUser(owner.getId(), owner.getRole(), false);
    }

    // 토큰은 요청마다 현재 테스트 시각으로 발급한다
    protected String bearer(User owner) {
        return "Bearer " + jwtProvider.createAccessToken(owner.getId());
    }

    protected static String newRequestId() {
        return UUID.randomUUID().toString();
    }
}
