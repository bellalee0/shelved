package com.shelved.domain.user.repository;

import static com.shelved.common.exception.ErrorCode.USER_NOT_FOUND;

import com.shelved.common.entity.User;
import com.shelved.common.exception.CustomException;
import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByNicknameLowerAndIdNot(String nicknameLower, Long id);

    // 같은 사용자의 요청을 직렬화할 때 쓰는 비관적 잠금 조회 (예: 같은 사건 세션 동시 생성 방지)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from User u where u.id = :userId")
    Optional<User> findWithLockById(@Param("userId") Long userId);

    default User findUserById(Long userId) {
        return findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
    }

    default User findUserWithLockById(Long userId) {
        return findWithLockById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
    }

    default User findUserByGoogleSub(String googleSub) {
        return findByGoogleSub(googleSub)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
    }
}
