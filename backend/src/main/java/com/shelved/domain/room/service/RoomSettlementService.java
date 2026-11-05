package com.shelved.domain.room.service;

import com.shelved.common.entity.Room;
import com.shelved.common.enums.RoomStatus;
import com.shelved.common.utils.SafeExceptionLog;
import com.shelved.domain.room.repository.RoomRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 호출한 트랜잭션과 별도로, 세션 하나를 짧은 새 트랜잭션에서 잠그고 정산한다.
 * - 이탈 감지 주기 점검: 세션마다 독립 트랜잭션이라 한 세션의 실패가 다른 세션을 막지 않는다
 * - 거부 응답의 정산 보존: 정산 뒤 409를 던지면 요청 트랜잭션이 롤백되므로, 롤백 후 같은 정산을 새 트랜잭션에서 다시 확정한다
 */
@Slf4j
@Service
public class RoomSettlementService {

    private final RoomRepository roomRepository;
    private final RoomTimeSettler roomTimeSettler;
    private final Clock clock;
    private final TransactionTemplate newTransaction;

    public RoomSettlementService(
            RoomRepository roomRepository,
            RoomTimeSettler roomTimeSettler,
            Clock clock,
            PlatformTransactionManager transactionManager
    ) {
        this.roomRepository = roomRepository;
        this.roomTimeSettler = roomTimeSettler;
        this.clock = clock;
        this.newTransaction = new TransactionTemplate(transactionManager);
        this.newTransaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    /**
     * 정산 1·2만 적용한다 (주기 점검). 잠금 후 현재 값으로 다시 판단하므로 후보 조회 뒤 하트비트가 왔으면 바뀌지 않는다
     */
    public void settleRoom(Long roomId) {
        newTransaction.executeWithoutResult(status -> {
            Room room = roomRepository.findRoomWithLockById(roomId);
            roomTimeSettler.settle(room, LocalDateTime.now(clock));
        });
    }

    /**
     * 현재 트랜잭션이 커밋되지 않고 끝나면(거부 예외로 롤백) 같은 세션을 새 트랜잭션에서 다시 정산한다.
     * 정산 1·2는 저장된 값만으로 같은 결과가 나오고, expireIfTimeUp이면 접속 확인 요청이 확인한 시간 만료도 확정한다.
     * 롤백으로 잠금이 풀린 뒤(afterCompletion) 실행되므로 409 응답이 나가기 전에 커밋된다
     */
    public void keepSettlementOnRollback(Long roomId, boolean expireIfTimeUp) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == STATUS_COMMITTED) {
                    return;
                }
                try {
                    resettle(roomId, expireIfTimeUp);
                } catch (RuntimeException e) {
                    log.error("거부 응답 후 세션 정산 실패 roomId={} exception={}{}",
                            roomId, SafeExceptionLog.causeChain(e), SafeExceptionLog.rootCauseFrames(e));
                }
            }
        });
    }

    private void resettle(Long roomId, boolean expireIfTimeUp) {
        newTransaction.executeWithoutResult(status -> {
            Room room = roomRepository.findRoomWithLockById(roomId);
            LocalDateTime now = LocalDateTime.now(clock);
            roomTimeSettler.settle(room, now);
            if (expireIfTimeUp && room.getStatus() == RoomStatus.IN_PROGRESS && roomTimeSettler.isTimeUp(room, now)) {
                roomTimeSettler.expire(room, now);
            }
        });
    }
}
