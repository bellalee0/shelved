package com.shelved.common.jwt;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.IntegrationTestSupport;
import com.shelved.common.config.JwtProperties;
import com.shelved.common.constants.ValidationMessage;
import com.shelved.common.entity.User;
import com.shelved.common.enums.UserRole;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.user.repository.UserRepository;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 필터 단계 인증·인가 오류와 CORS. 오류 응답이 모두 CommonResponse 형식(success=false, data.code)인지 함께 확인한다.
 * 아직 컨트롤러가 없는 경로(/api/rooms, /api/admin/**, /api/cases)는 보안 필터를 통과하면 404 API_NOT_FOUND가 된다.
 */
class SecurityIntegrationTest extends IntegrationTestSupport {

    private static final String ALLOWED_ORIGIN = "http://localhost:5173";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtProvider jwtProvider;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void 인증필요API_토큰없음_LOGIN_REQUIRED() throws Exception {

        // given & when
        ResultActions result = mockMvc.perform(get("/api/users/me"));

        // then
        expectError(result, ErrorCode.LOGIN_REQUIRED);
    }

    @Test
    void 인증필요API_만료토큰_EXPIRED_TOKEN() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, true);
        Clock twoHoursAgo = Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(-2));
        String expiredToken = new JwtProvider(jwtProperties, twoHoursAgo).createAccessToken(user.getId());

        // when
        ResultActions result = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)));

        // then
        expectError(result, ErrorCode.EXPIRED_TOKEN);
    }

    @Test
    void 인증필요API_다른키로서명한토큰_INVALID_TOKEN() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, true);
        JwtProperties otherKey = new JwtProperties("another-fake-secret-key-for-forgery-0000", Duration.ofHours(1), Duration.ofDays(14));
        String forgedToken = new JwtProvider(otherKey, Clock.systemDefaultZone()).createAccessToken(user.getId());

        // when
        ResultActions result = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(forgedToken)));

        // then
        expectError(result, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 인증필요API_형식오류토큰_INVALID_TOKEN() throws Exception {

        // given & when
        ResultActions bearerResult = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt"));
        ResultActions schemeResult = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Basic abc"));

        // then
        expectError(bearerResult, ErrorCode.INVALID_TOKEN);
        expectError(schemeResult, ErrorCode.INVALID_TOKEN);
    }

    @Test
    void 관리자API_플레이어_ACCESS_DENIED() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, true);

        // when
        ResultActions result = mockMvc.perform(get("/api/admin/cases").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        expectError(result, ErrorCode.ACCESS_DENIED);
    }

    @Test
    void 관리자API_DB에서관리자로변경_기존토큰으로통과() throws Exception {

        // given: 토큰 발급 후 DB에서 역할만 바꾼다 (관리자 계정 수동 생성 절차)
        User user = saveUser(UserRole.PLAYER, true);
        String token = jwtProvider.createAccessToken(user.getId());
        jdbcTemplate.update("update users set role = 'ADMIN' where id = ?", user.getId());

        // when
        ResultActions result = mockMvc.perform(get("/api/admin/cases").header(HttpHeaders.AUTHORIZATION, bearer(token)));

        // then: 인가를 통과해 핸들러 조회 단계(404)까지 간다
        expectError(result, ErrorCode.API_NOT_FOUND);
    }

    @Test
    void 일반API_닉네임미설정_NICKNAME_REQUIRED() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, false);

        // when
        ResultActions result = mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        expectError(result, ErrorCode.NICKNAME_REQUIRED);
    }

    @Test
    void 내정보_닉네임미설정_통과() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, false);

        // when
        ResultActions result = mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, bearer(user)));

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.userId").value(user.getId()))
                .andExpect(jsonPath("$.data.nickname").isEmpty())
                .andExpect(jsonPath("$.data.role").value("PLAYER"))
                .andExpect(jsonPath("$.data.nicknameRequired").value(true));
    }

    @Test
    void 닉네임설정_설정직후같은토큰으로일반API통과() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, false);
        String token = jwtProvider.createAccessToken(user.getId());
        String nickname = "Nk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8);

        // when
        ResultActions updateResult = mockMvc.perform(patch("/api/users/me/nickname")
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"" + nickname + "\"}"));
        ResultActions roomsResult = mockMvc.perform(get("/api/rooms").header(HttpHeaders.AUTHORIZATION, bearer(token)));

        // then
        updateResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.data.nickname").value(nickname))
                .andExpect(jsonPath("$.data.nicknameRequired").value(false));
        expectError(roomsResult, ErrorCode.API_NOT_FOUND);
    }

    @Test
    void 닉네임설정_형식위반_INVALID_INPUT() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, false);

        // when
        ResultActions result = mockMvc.perform(patch("/api/users/me/nickname")
                .header(HttpHeaders.AUTHORIZATION, bearer(user))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"nickname\": \"탐정!\"}"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ValidationMessage.NICKNAME_PATTERN))
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"));
    }

    @Test
    void 인증불필요API_토큰없이통과() throws Exception {

        // given & when
        ResultActions casesResult = mockMvc.perform(get("/api/cases"));
        ResultActions coverResult = mockMvc.perform(get("/uploads/covers/none.png"));
        ResultActions logoutResult = mockMvc.perform(post("/api/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"unknown-token\"}"));
        ResultActions refreshResult = mockMvc.perform(post("/api/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"refreshToken\": \"unknown-token\"}"));

        // then: 인증 오류(401) 대신 각 API의 결과가 나온다
        expectError(casesResult, ErrorCode.API_NOT_FOUND);
        expectError(coverResult, ErrorCode.API_NOT_FOUND);
        logoutResult.andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data").isEmpty());
        expectError(refreshResult, ErrorCode.INVALID_REFRESH_TOKEN);
    }

    @Test
    void 인증불필요API_만료토큰이있어도통과() throws Exception {

        // given
        User user = saveUser(UserRole.PLAYER, true);
        Clock twoHoursAgo = Clock.offset(Clock.systemDefaultZone(), Duration.ofHours(-2));
        String expiredToken = new JwtProvider(jwtProperties, twoHoursAgo).createAccessToken(user.getId());

        // when
        ResultActions result = mockMvc.perform(get("/api/cases").header(HttpHeaders.AUTHORIZATION, bearer(expiredToken)));

        // then
        expectError(result, ErrorCode.API_NOT_FOUND);
    }

    @Test
    void Google로그인_clientId미설정_GOOGLE_TOKEN_INVALID() throws Exception {

        // given: 테스트 프로필은 GOOGLE_CLIENT_ID가 비어 있다

        // when
        ResultActions result = mockMvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"idToken\": \"any-id-token\"}"));

        // then
        expectError(result, ErrorCode.GOOGLE_TOKEN_INVALID);
    }

    @Test
    void Google로그인_idToken누락_INVALID_INPUT() throws Exception {

        // given & when
        ResultActions result = mockMvc.perform(post("/api/auth/google")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ValidationMessage.ID_TOKEN_NOT_BLANK))
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"));
    }

    @Test
    void CORS사전요청_허용출처_인증없이통과() throws Exception {

        // given & when
        ResultActions result = mockMvc.perform(options("/api/users/me/nickname")
                .header(HttpHeaders.ORIGIN, ALLOWED_ORIGIN)
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "PATCH")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization, content-type, x-connection-id"));

        // then
        result.andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ALLOWED_ORIGIN))
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void CORS사전요청_허용하지않은출처_거부() throws Exception {

        // given & when
        ResultActions result = mockMvc.perform(options("/api/users/me")
                .header(HttpHeaders.ORIGIN, "http://evil.example.com")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));

        // then
        result.andExpect(status().isForbidden())
                .andExpect(header().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
    }

    private User saveUser(UserRole role, boolean withNickname) {
        User user = new User("google-" + UUID.randomUUID(), role);
        if (withNickname) {
            user.updateNickname("Nk" + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
        return userRepository.save(user);
    }

    private String bearer(User user) {
        return bearer(jwtProvider.createAccessToken(user.getId()));
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }

    private static void expectError(ResultActions result, ErrorCode errorCode) throws Exception {
        result.andExpect(status().is(errorCode.getStatus().value()))
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(errorCode.getMessage()))
                .andExpect(jsonPath("$.data.code").value(errorCode.name()))
                .andExpect(jsonPath("$.timestamp").exists());
    }
}
