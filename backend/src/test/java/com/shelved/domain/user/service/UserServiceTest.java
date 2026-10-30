package com.shelved.domain.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;

import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.user.dto.UserMeResponse;
import com.shelved.domain.user.dto.UserNicknameUpdateRequest;
import com.shelved.domain.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    private static final Long USER_ID = 7L;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserService userService;

    @Test
    void getMyInfo_닉네임미설정_nicknameRequired() {

        // given
        given(userRepository.findUserById(USER_ID)).willReturn(newUser());

        // when
        UserMeResponse response = userService.getMyInfo(USER_ID);

        // then
        assertThat(response.userId()).isEqualTo(USER_ID);
        assertThat(response.nickname()).isNull();
        assertThat(response.role()).isEqualTo(UserRole.PLAYER);
        assertThat(response.nicknameRequired()).isTrue();
    }

    @Test
    void updateNickname_성공_입력대소문자보존() {

        // given
        User user = newUser();
        given(userRepository.findUserById(USER_ID)).willReturn(user);
        given(userRepository.existsByNicknameLowerAndIdNot("detective", USER_ID)).willReturn(false);

        // when
        UserMeResponse response = userService.updateNickname(USER_ID, new UserNicknameUpdateRequest("DeTective"));

        // then
        assertThat(response.nickname()).isEqualTo("DeTective");
        assertThat(response.nicknameRequired()).isFalse();
        assertThat(user.getNicknameLower()).isEqualTo("detective");
    }

    @Test
    void updateNickname_중복_예외() {

        // given
        given(userRepository.findUserById(USER_ID)).willReturn(newUser());
        given(userRepository.existsByNicknameLowerAndIdNot("탐정7", USER_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(USER_ID, new UserNicknameUpdateRequest("탐정7")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_EXIST);
    }

    @Test
    void updateNickname_대소문자만다른중복_예외() {

        // given: 다른 사용자가 "detective"를 사용 중
        given(userRepository.findUserById(USER_ID)).willReturn(newUser());
        given(userRepository.existsByNicknameLowerAndIdNot("detective", USER_ID)).willReturn(true);

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(USER_ID, new UserNicknameUpdateRequest("DETECTIVE")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_EXIST);
    }

    @Test
    void updateNickname_본인기존닉네임_성공() {

        // given: 본인은 중복 검사에서 제외된다 (IdNot)
        User user = newUser();
        user.updateNickname("Detective");
        given(userRepository.findUserById(USER_ID)).willReturn(user);
        given(userRepository.existsByNicknameLowerAndIdNot("detective", USER_ID)).willReturn(false);

        // when
        UserMeResponse response = userService.updateNickname(USER_ID, new UserNicknameUpdateRequest("detective"));

        // then
        assertThat(response.nickname()).isEqualTo("detective");
    }

    @Test
    void updateNickname_동시요청으로UNIQUE위반_중복예외() {

        // given
        given(userRepository.findUserById(USER_ID)).willReturn(newUser());
        given(userRepository.existsByNicknameLowerAndIdNot("detective", USER_ID)).willReturn(false);
        willThrow(new DataIntegrityViolationException("uk_users_nickname_lower")).given(userRepository).flush();

        // when & then
        assertThatThrownBy(() -> userService.updateNickname(USER_ID, new UserNicknameUpdateRequest("Detective")))
                .isInstanceOf(CustomException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.NICKNAME_EXIST);
    }

    private static User newUser() {
        User user = new User("google-sub", UserRole.PLAYER);
        ReflectionTestUtils.setField(user, "id", USER_ID);
        return user;
    }
}
