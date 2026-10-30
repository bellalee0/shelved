package com.shelved.common.utils;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.config.JacksonConfig;
import com.shelved.common.exception.GlobalExceptionHandler;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@WebMvcTest(controllers = TrimStringDeserializerTest.TrimTestController.class)
@Import({TrimStringDeserializerTest.TrimTestController.class, JacksonConfig.class, GlobalExceptionHandler.class})
class TrimStringDeserializerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void deserialize_앞뒤공백_제거() throws Exception {

        // given
        String body = """
                {"name": "  집사  ", "content": "  녹음기\\n  ", "tags": [" 서재 ", "정원 "]}
                """;

        // when
        ResultActions result = postJson("/test/trim/record", body);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("집사"))
                .andExpect(jsonPath("$.tags[0]").value("서재"))
                .andExpect(jsonPath("$.tags[1]").value("정원"));
    }

    @Test
    void deserialize_전각공백과줄바꿈_제거() throws Exception {

        // given
        String body = """
                {"name": "\\u3000\\n집사\\t\\u3000", "content": null, "tags": []}
                """;

        // when
        ResultActions result = postJson("/test/trim/record", body);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("집사"));
    }

    @Test
    void deserialize_공백만입력_NotBlank검증실패() throws Exception {

        // given
        String body = """
                {"name": "   ", "content": "메모", "tags": []}
                """;

        // when
        ResultActions result = postJson("/test/trim/record", body);

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.detail.field").value("name"));
    }

    @Test
    void deserialize_NoTrim이붙은record필드_원문보존() throws Exception {

        // given
        String body = """
                {"name": "집사", "content": "  녹음기\\n  ", "tags": []}
                """;

        // when
        ResultActions result = postJson("/test/trim/record", body);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("  녹음기\n  "));
    }

    @Test
    void deserialize_NoTrim이붙은클래스필드_원문보존() throws Exception {

        // given
        String body = """
                {"name": "  집사  ", "content": "\\n  녹음기  "}
                """;

        // when
        ResultActions result = postJson("/test/trim/class", body);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("집사"))
                .andExpect(jsonPath("$.content").value("\n  녹음기  "));
    }

    @Test
    void serialize_LocalDateTime_오프셋없는ISO문자열() throws Exception {

        // given
        String body = "{}";

        // when
        ResultActions result = postJson("/test/datetime", body);

        // then
        result.andExpect(status().isOk())
                .andExpect(jsonPath("$.at").value("2026-10-05T14:30:00"));
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    record TrimRecordRequest(
            @NotBlank(message = "이름은 필수입니다.")
            String name,
            @NoTrim
            String content,
            List<String> tags
    ) {
    }

    @Getter
    @NoArgsConstructor
    static class TrimClassRequest {

        private String name;

        @NoTrim
        private String content;
    }

    // 문자열 처리 검증용 컨트롤러 (테스트 소스에만 존재)
    @RestController
    static class TrimTestController {

        @PostMapping("/test/trim/record")
        public TrimRecordRequest trimRecord(@Valid @RequestBody TrimRecordRequest request) {
            return request;
        }

        @PostMapping("/test/trim/class")
        public TrimClassRequest trimClass(@RequestBody TrimClassRequest request) {
            return request;
        }

        @PostMapping("/test/datetime")
        public Map<String, LocalDateTime> datetime() {
            return Map.of("at", LocalDateTime.of(2026, 10, 5, 14, 30));
        }
    }
}
