package com.shelved.domain.auth.repository;

import static com.shelved.common.exception.ErrorCode.INVALID_REFRESH_TOKEN;

import com.shelved.common.entity.RefreshToken;
import com.shelved.common.exception.CustomException;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * 유효한(무효화·만료되지 않은) 토큰만 무효화하고, 무효화한 행 수를 반환한다.
     * 같은 토큰으로 동시에 요청하면 뒤의 요청은 앞 트랜잭션의 커밋을 기다린 뒤 조건을 다시 확인하므로 0을 받는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            update RefreshToken r
               set r.revokedAt = :now
             where r.tokenHash = :tokenHash
               and r.revokedAt is null
               and r.expiresAt > :now
            """)
    int revokeActiveToken(String tokenHash, LocalDateTime now);

    @Query("select r from RefreshToken r join fetch r.user where r.tokenHash = :tokenHash")
    Optional<RefreshToken> findWithUserByTokenHash(String tokenHash);

    default RefreshToken findRefreshTokenWithUser(String tokenHash) {
        return findWithUserByTokenHash(tokenHash)
                .orElseThrow(() -> new CustomException(INVALID_REFRESH_TOKEN));
    }
}
