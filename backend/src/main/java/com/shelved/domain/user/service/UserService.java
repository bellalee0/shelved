package com.shelved.domain.user.service;

import com.shelved.common.entity.User;
import com.shelved.common.exception.CustomException;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.user.dto.UserMeResponse;
import com.shelved.domain.user.dto.UserNicknameUpdateRequest;
import com.shelved.domain.user.repository.UserRepository;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {

    private final UserRepository userRepository;

    /**
     * 내 정보 조회
     */
    @Transactional(readOnly = true)
    public UserMeResponse getMyInfo(Long userId) {

        User user = userRepository.findUserById(userId);

        return UserMeResponse.from(user);
    }

    /**
     * 닉네임 설정·변경. 영문 대소문자를 구분하지 않고 다른 사용자와의 중복을 막는다 (본인의 기존 닉네임은 허용)
     */
    @Transactional
    public UserMeResponse updateNickname(Long userId, UserNicknameUpdateRequest request) {

        User user = userRepository.findUserById(userId);

        String nicknameLower = request.nickname().toLowerCase(Locale.ROOT);
        if (userRepository.existsByNicknameLowerAndIdNot(nicknameLower, userId)) {
            throw new CustomException(ErrorCode.NICKNAME_EXIST);
        }

        user.updateNickname(request.nickname());

        // 같은 닉네임을 동시에 요청하면 위 검사를 함께 통과할 수 있으므로 nickname_lower UNIQUE 위반도 중복으로 처리한다
        try {
            userRepository.flush();
        } catch (DataIntegrityViolationException e) {
            throw new CustomException(ErrorCode.NICKNAME_EXIST);
        }

        return UserMeResponse.from(user);
    }
}
