package com.shelved.common.jwt;

import com.shelved.common.config.CorsProperties;
import com.shelved.common.exception.ErrorCode;
import com.shelved.domain.user.repository.UserRepository;
import jakarta.servlet.DispatcherType;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.intercept.AuthorizationFilter;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 인증·인가 설정
 * - 토큰: Authorization 헤더의 JWT 액세스 토큰 (JwtAuthenticationFilter)
 * - 인증 불필요 경로: SecurityPaths.PUBLIC / 관리자 전용: SecurityPaths.ADMIN / 닉네임 검사: NicknameRequiredFilter
 * - 오류 응답: SecurityErrorResponder로 CommonResponse 형식을 직접 쓴다
 */
@Configuration
@EnableWebSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private static final String CONNECTION_ID_HEADER = "X-Connection-Id";

    private final JwtProvider jwtProvider;
    private final UserRepository userRepository;
    private final SecurityErrorResponder securityErrorResponder;
    private final CorsProperties corsProperties;

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {

        http
                // 토큰을 쿠키가 아닌 Authorization 헤더로만 주고받는다. 브라우저가 요청에 인증 정보를 자동으로 붙이지 않으므로
                // CSRF(다른 사이트가 사용자의 쿠키로 요청을 위조하는 공격) 대상이 아니어서 끈다.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // 요청마다 토큰으로 인증하고 서버 세션을 만들지 않는다
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .exceptionHandling(exception -> exception
                        // 인증 없음·토큰 오류 → LOGIN_REQUIRED / EXPIRED_TOKEN / INVALID_TOKEN
                        .authenticationEntryPoint((request, response, e) ->
                                securityErrorResponder.write(response, JwtAuthenticationFilter.resolveAuthError(request)))
                        // 인증은 됐지만 권한 없음 (관리자 전용 경로)
                        .accessDeniedHandler((request, response, e) ->
                                securityErrorResponder.write(response, ErrorCode.ACCESS_DENIED)))
                .authorizeHttpRequests(auth -> auth
                        // 컨트롤러 밖에서 난 오류의 /error 전달이 401로 바뀌지 않게 한다
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        // 브라우저의 CORS 사전 요청
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(SecurityPaths.PUBLIC).permitAll()
                        .requestMatchers(SecurityPaths.ADMIN).hasRole("ADMIN")
                        .anyRequest().authenticated())
                .addFilterBefore(new JwtAuthenticationFilter(jwtProvider, userRepository), UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new NicknameRequiredFilter(securityErrorResponder), AuthorizationFilter.class);

        return http.build();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {

        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(HttpHeaders.AUTHORIZATION, HttpHeaders.CONTENT_TYPE, CONNECTION_ID_HEADER));
        // 쿠키를 쓰지 않으므로 자격 증명(credentials)을 허용하지 않는다
        config.setAllowCredentials(false);
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);

        return source;
    }
}
