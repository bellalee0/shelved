package com.shelved.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.domain.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

/**
 * DB 오류 상세(제약 위반 키 값)가 예외 메시지와 로그에 남지 않는지 확인한다.
 * logServerErrorDetail=false(드라이버)와 org.hibernate.orm.jdbc.error=OFF(로거)를 함께 검증한다.
 */
@ExtendWith(OutputCaptureExtension.class)
class DbErrorLogMaskingIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserRepository userRepository;

    @Test
    void saveAndFlush_nicknameLower중복_예외메시지와로그에값미노출(CapturedOutput output) {

        // given
        String marker = "sensitive-marker-" + UUID.randomUUID().toString().substring(0, 8);
        User first = new User("google-sub-" + UUID.randomUUID(), UserRole.PLAYER);
        first.updateNickname(marker);
        userRepository.saveAndFlush(first);

        User duplicate = new User("google-sub-" + UUID.randomUUID(), UserRole.PLAYER);
        duplicate.updateNickname(marker.toUpperCase());

        // when
        Throwable thrown = catchThrowable(() -> userRepository.saveAndFlush(duplicate));

        // then
        assertThat(thrown).isInstanceOf(DataIntegrityViolationException.class);
        assertThat(thrown).rootCause().hasMessageContaining("uk_users_nickname_lower");
        for (Throwable t = thrown; t != null; t = t.getCause()) {
            assertThat(t.getMessage()).doesNotContainIgnoringCase(marker);
        }
        assertThat(output.getAll()).doesNotContainIgnoringCase(marker);
    }
}
