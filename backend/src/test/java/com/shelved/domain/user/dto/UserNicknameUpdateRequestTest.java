package com.shelved.domain.user.dto;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.constants.ValidationMessage;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UserNicknameUpdateRequestTest {

    private static ValidatorFactory validatorFactory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        validatorFactory = Validation.buildDefaultValidatorFactory();
        validator = validatorFactory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        validatorFactory.close();
    }

    @ParameterizedTest
    @ValueSource(strings = {"탐정", "Detective12", "abcdefghijkl", "홍길동Holmes1"})
    void nickname_허용형식_통과(String nickname) {

        // given
        UserNicknameUpdateRequest request = new UserNicknameUpdateRequest(nickname);

        // when
        Set<ConstraintViolation<UserNicknameUpdateRequest>> violations = validator.validate(request);

        // then
        assertThat(violations).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"a", "abcdefghijklm"})
    void nickname_길이위반_검증메시지(String nickname) {

        // given
        UserNicknameUpdateRequest request = new UserNicknameUpdateRequest(nickname);

        // when
        Set<String> messages = messagesOf(request);

        // then
        assertThat(messages).containsExactly(ValidationMessage.NICKNAME_SIZE);
    }

    @ParameterizedTest
    @ValueSource(strings = {"탐정 7", "탐정_7", "ㄱㄴㄷ", "탐정!", "ｄｅｔｅｃｔ"})
    void nickname_허용되지않는문자_검증메시지(String nickname) {

        // given
        UserNicknameUpdateRequest request = new UserNicknameUpdateRequest(nickname);

        // when
        Set<String> messages = messagesOf(request);

        // then
        assertThat(messages).containsExactly(ValidationMessage.NICKNAME_PATTERN);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " "})
    void nickname_빈값_검증메시지(String nickname) {

        // given
        UserNicknameUpdateRequest request = new UserNicknameUpdateRequest(nickname);

        // when
        Set<String> messages = messagesOf(request);

        // then
        assertThat(messages).contains(ValidationMessage.NICKNAME_NOT_BLANK);
    }

    private static Set<String> messagesOf(UserNicknameUpdateRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }
}
