package com.shelved.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.user.dto.UserNicknameUpdateRequest;
import com.shelved.domain.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class UserIntegrationTest extends IntegrationTestSupport {

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Test
    void updateNickname_같은닉네임동시요청_하나만성공() throws Exception {

        // given
        String nickname = uniqueNickname();
        int threadCount = 5;
        List<Long> userIdList = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            userIdList.add(saveUser().getId());
        }
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<ErrorCode>> tasks = userIdList.stream()
                .<Callable<ErrorCode>>map(userId -> () -> {
                    ready.countDown();
                    start.await();
                    try {
                        userService.updateNickname(userId, new UserNicknameUpdateRequest(nickname));
                        return null;
                    } catch (CustomException e) {
                        return e.getErrorCode();
                    }
                })
                .toList();

        // when
        List<ErrorCode> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newFixedThreadPool(threadCount)) {
            List<Future<ErrorCode>> futures = tasks.stream().map(executor::submit).toList();
            ready.await();
            start.countDown();
            for (Future<ErrorCode> future : futures) {
                results.add(future.get());
            }
        }

        // then
        assertThat(results).filteredOn(code -> code == null).hasSize(1);
        assertThat(results).filteredOn(code -> code != null)
                .hasSize(threadCount - 1)
                .containsOnly(ErrorCode.NICKNAME_EXIST);
    }

    @Test
    void updateNickname_대소문자만다른닉네임_중복예외() {

        // given
        String nickname = uniqueNickname();
        Long ownerId = saveUser().getId();
        Long otherId = saveUser().getId();
        userService.updateNickname(ownerId, new UserNicknameUpdateRequest(nickname.toLowerCase()));

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(otherId, new UserNicknameUpdateRequest(nickname.toUpperCase())))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_EXIST);
    }

    @Test
    void updateNickname_본인기존닉네임대소문자변경_성공() {

        // given
        String nickname = uniqueNickname();
        Long userId = saveUser().getId();
        userService.updateNickname(userId, new UserNicknameUpdateRequest(nickname.toLowerCase()));

        // when
        String updated = userService.updateNickname(userId, new UserNicknameUpdateRequest(nickname.toUpperCase())).nickname();

        // then
        assertThat(updated).isEqualTo(nickname.toUpperCase());
    }

    private User saveUser() {
        return userRepository.save(new User("google-" + UUID.randomUUID(), UserRole.PLAYER));
    }

    // 테스트끼리 같은 DB를 쓰므로 겹치지 않는 영문·숫자 닉네임 (10자)
    private static String uniqueNickname() {
        return "Nk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
    }
}
