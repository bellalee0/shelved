package com.shelved.common.entity;

import com.shelved.common.enums.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.Locale;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "users",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_users_google_sub", columnNames = "google_sub"),
                @UniqueConstraint(name = "uk_users_nickname_lower", columnNames = "nickname_lower")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "google_sub", nullable = false)
    private String googleSub;

    // 최초 로그인 시 null. 표시에는 입력한 대소문자를 그대로 쓴다
    @Column(name = "nickname")
    private String nickname;

    // 영문 대소문자를 구분하지 않는 중복 검사용 정규화 값 (Locale.ROOT 소문자)
    @Column(name = "nickname_lower")
    private String nicknameLower;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private UserRole role;

    public User(String googleSub, UserRole role) {
        this.googleSub = googleSub;
        this.role = role;
    }

    /**
     * 닉네임 변경. 형식·중복 검증은 호출하는 쪽에서 먼저 한다.
     */
    public void updateNickname(String nickname) {
        this.nickname = nickname;
        this.nicknameLower = nickname.toLowerCase(Locale.ROOT);
    }
}
