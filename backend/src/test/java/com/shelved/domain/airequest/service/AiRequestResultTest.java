package com.shelved.domain.airequest.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.entity.AiRequest;
import com.shelved.common.entity.json.AiRequestInput;
import com.shelved.common.enums.AiRequestType;
import com.shelved.common.enums.SuccessMessage;
import com.shelved.domain.airequest.dto.AiRequestStatusResponse;
import java.time.Duration;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class AiRequestResultTest {

    @Test
    void toResponseEntity_완료는200_성공메시지와스냅샷() {

        // given
        JsonNode snapshot = JsonMapper.builder().build().readTree("{\"answer\":\"부엌에 있었습니다.\"}");

        // when
        ResponseEntity<CommonResponse<Object>> response =
                new AiRequestResult.Completed(snapshot).toResponseEntity(SuccessMessage.AI_REQUEST_GET_SUCCESS);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody().getMessage()).isEqualTo(SuccessMessage.AI_REQUEST_GET_SUCCESS.getMessage());
        assertThat(response.getBody().getData()).isSameAs(snapshot);
    }

    @Test
    void toResponseEntity_처리중은202_상태응답() {

        // given
        AiRequest aiRequest = new AiRequest(null, "req-1", AiRequestType.HINT, null,
                AiRequestInput.question("종소리는 몇 번 울렸나요?"), LocalDateTime.of(2026, 11, 12, 14, 0));
        AiRequestStatusResponse status = AiRequestStatusResponse.from(aiRequest, Duration.ofSeconds(10));

        // when
        ResponseEntity<CommonResponse<Object>> response =
                new AiRequestResult.Processing(status).toResponseEntity(SuccessMessage.AI_REQUEST_GET_SUCCESS);

        // then
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
        assertThat(response.getBody().getMessage()).isEqualTo(SuccessMessage.AI_REQUEST_PROCESSING_SUCCESS.getMessage());
        assertThat(response.getBody().getData()).isSameAs(status);
    }
}
