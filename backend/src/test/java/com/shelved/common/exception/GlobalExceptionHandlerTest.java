package com.shelved.common.exception;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.shelved.common.config.JacksonConfig;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@WebMvcTest(controllers = GlobalExceptionHandlerTest.ExceptionTestController.class)
@Import({GlobalExceptionHandlerTest.ExceptionTestController.class, JacksonConfig.class})
class GlobalExceptionHandlerTest {

    private static final String TIMESTAMP_PATTERN = "^\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d{1,3})?$";
    private static final String INTERNAL_MESSAGE = "내부 구현 정보가 담긴 메시지";

    @Autowired
    private MockMvc mockMvc;

    @Test
    void handleCustomException_detail포함_공통응답() throws Exception {

        // given
        String url = "/test/custom";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isConflict())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(ErrorCode.ROOM_ALREADY_EXISTS.getMessage()))
                .andExpect(jsonPath("$.data.code").value("ROOM_ALREADY_EXISTS"))
                .andExpect(jsonPath("$.data.detail.roomId").value(7))
                .andExpect(jsonPath("$.timestamp").value(matchesPattern(TIMESTAMP_PATTERN)));
    }

    @Test
    void handleCustomException_detail없음_detail은null() throws Exception {

        // given
        String url = "/test/custom-no-detail";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.data.code").value("ROOM_NOT_FOUND"))
                .andExpect(jsonPath("$.data.detail").isEmpty());
    }

    @Test
    void handleMethodArgumentNotValid_필수값누락_INVALID_INPUT() throws Exception {

        // given
        String body = """
                {"title": "", "count": 1}
                """;

        // when
        ResultActions result = postJson("/test/body", body);

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("제목은 필수입니다."))
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.detail.field").value("title"));
    }

    @Test
    void handleHttpMessageNotReadable_JSON형식오류_INVALID_REQUEST_BODY() throws Exception {

        // given
        String body = "{\"title\": ";

        // when
        ResultActions result = postJson("/test/body", body);

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_REQUEST_BODY.getMessage()))
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void handleHttpMessageNotReadable_정수필드에소수_INVALID_INPUT() throws Exception {

        // given
        String body = """
                {"title": "녹음기", "count": 1.5}
                """;

        // when
        ResultActions result = postJson("/test/body", body);

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("입력값의 형식이 올바르지 않습니다."))
                .andExpect(jsonPath("$.data.code").value("INVALID_INPUT"))
                .andExpect(jsonPath("$.data.detail.field").value("count"));
    }

    @Test
    void handleHttpMediaTypeNotSupported_JSON아닌본문_INVALID_REQUEST_BODY() throws Exception {

        // given
        String body = "title=녹음기";

        // when
        ResultActions result = mockMvc.perform(post("/test/body")
                .contentType(MediaType.TEXT_PLAIN)
                .content(body));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_REQUEST_BODY"));
    }

    @Test
    void handleMethodArgumentTypeMismatch_경로변수타입불일치_INVALID_PARAMETER() throws Exception {

        // given
        String url = "/test/items/abc?page=0";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.INVALID_PARAMETER.getMessage()))
                .andExpect(jsonPath("$.data.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.data.detail.field").value("itemId"));
    }

    @Test
    void handleMissingRequestValue_쿼리파라미터누락_INVALID_PARAMETER() throws Exception {

        // given
        String url = "/test/items/1";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.data.detail.field").value("page"));
    }

    @Test
    void handleHandlerMethodValidation_쿼리파라미터제약위반_INVALID_PARAMETER() throws Exception {

        // given
        String url = "/test/items/1?page=-1";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.data.code").value("INVALID_PARAMETER"))
                .andExpect(jsonPath("$.data.detail.field").value("page"));
    }

    @Test
    void handleNotFound_없는경로_API_NOT_FOUND() throws Exception {

        // given
        String url = "/api/not-exists";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value(ErrorCode.API_NOT_FOUND.getMessage()))
                .andExpect(jsonPath("$.data.code").value("API_NOT_FOUND"));
    }

    @Test
    void handleMethodNotSupported_지원하지않는메서드_METHOD_NOT_ALLOWED() throws Exception {

        // given
        String url = "/test/custom";

        // when
        ResultActions result = mockMvc.perform(delete(url));

        // then
        result.andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.message").value(ErrorCode.METHOD_NOT_ALLOWED.getMessage()))
                .andExpect(jsonPath("$.data.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void handleMaxUploadSizeExceeded_업로드크기초과_FILE_TOO_LARGE() throws Exception {

        // given
        String url = "/test/upload";

        // when
        ResultActions result = mockMvc.perform(post(url));

        // then
        result.andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(ErrorCode.FILE_TOO_LARGE.getMessage()))
                .andExpect(jsonPath("$.data.code").value("FILE_TOO_LARGE"))
                .andExpect(jsonPath("$.data.detail.maxBytes").value(5 * 1024 * 1024));
    }

    @Test
    void handleException_예상하지못한예외_INTERNAL_SERVER_ERROR내부정보미노출() throws Exception {

        // given
        String url = "/test/unexpected";

        // when
        ResultActions result = mockMvc.perform(get(url));

        // then
        result.andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value(ErrorCode.INTERNAL_SERVER_ERROR.getMessage()))
                .andExpect(jsonPath("$.data.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.data.detail").isEmpty());
        String responseBody = result.andReturn().getResponse().getContentAsString();
        assertThat(responseBody).doesNotContain(INTERNAL_MESSAGE, "IllegalStateException", "at com.shelved");
    }

    private ResultActions postJson(String url, String body) throws Exception {
        return mockMvc.perform(post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body));
    }

    record TestBodyRequest(
            @NotBlank(message = "제목은 필수입니다.")
            String title,
            Integer count
    ) {
    }

    // 예외 변환 검증용 컨트롤러 (테스트 소스에만 존재)
    @RestController
    static class ExceptionTestController {

        @GetMapping("/test/custom")
        public void custom() {
            throw new CustomException(ErrorCode.ROOM_ALREADY_EXISTS, Map.of("roomId", 7));
        }

        @GetMapping("/test/custom-no-detail")
        public void customNoDetail() {
            throw new CustomException(ErrorCode.ROOM_NOT_FOUND);
        }

        @PostMapping("/test/body")
        public void body(@Valid @RequestBody TestBodyRequest request) {
        }

        @GetMapping("/test/items/{itemId}")
        public void items(@PathVariable Long itemId, @RequestParam @Min(0) int page) {
        }

        @PostMapping("/test/upload")
        public void upload() {
            throw new MaxUploadSizeExceededException(-1);
        }

        @GetMapping("/test/unexpected")
        public void unexpected() {
            throw new IllegalStateException(INTERNAL_MESSAGE);
        }
    }
}
