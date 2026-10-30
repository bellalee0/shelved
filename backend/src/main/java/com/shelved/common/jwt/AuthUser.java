package com.shelved.common.jwt;

import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import java.util.Collection;
import java.util.List;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 인증된 사용자. 컨트롤러에서 @AuthenticationPrincipal AuthUser authUser로 받는다.
 * 요청마다 DB의 현재 값으로 만든다 (DB에서 바꾼 관리자 역할, 방금 설정한 닉네임이 바로 반영되도록).
 */
@Getter
@RequiredArgsConstructor
public class AuthUser {

    private final Long id;
    private final UserRole role;
    private final boolean nicknameRequired;

    public static AuthUser from(User user) {
        return new AuthUser(
                user.getId(),
                user.getRole(),
                user.getNickname() == null
        );
    }

    public Collection<GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
