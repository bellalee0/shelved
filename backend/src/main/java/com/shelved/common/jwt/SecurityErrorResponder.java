package com.shelved.common.jwt;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.exception.ErrorCode;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

/**
 * 필터 단계의 인증·인가 오류 응답 작성
 * 필터 예외는 GlobalExceptionHandler까지 오지 않으므로 같은 CommonResponse 형식으로 직접 쓴다.
 * 로그에는 오류 코드만 남기고 토큰·스택 트레이스는 남기지 않는다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SecurityErrorResponder {

    private final ObjectMapper objectMapper;

    public void write(HttpServletResponse response, ErrorCode errorCode) throws IOException {

        log.info("SecurityException: {}", errorCode.name());

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), CommonResponse.exception(errorCode));
    }
}
