package com.shelved.domain.user.repository;

import static com.shelved.common.exception.ErrorCode.USER_NOT_FOUND;

import com.shelved.common.entity.User;
import com.shelved.common.exception.CustomException;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByGoogleSub(String googleSub);

    boolean existsByNicknameLowerAndIdNot(String nicknameLower, Long id);

    default User findUserById(Long userId) {
        return findById(userId)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
    }

    default User findUserByGoogleSub(String googleSub) {
        return findByGoogleSub(googleSub)
                .orElseThrow(() -> new CustomException(USER_NOT_FOUND));
    }
}
