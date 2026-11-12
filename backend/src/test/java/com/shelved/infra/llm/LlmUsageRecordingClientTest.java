package com.shelved.infra.llm;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.shelved.common.enums.LlmCallStatus;
import com.shelved.common.enums.LlmRequestType;
import com.shelved.domain.llmusage.service.LlmUsageLogService;
import com.shelved.domain.llmusage.service.LlmUsageRecord;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.support.TransactionSynchronizationManager;

class LlmUsageRecordingClientTest {

    private static final LlmCallContext GAME_CONTEXT = LlmCallContext.game(12L, 3L);

    private final FakeLlmClient fakeLlmClient = new FakeLlmClient();
    private final LlmUsageLogService llmUsageLogService = mock(LlmUsageLogService.class);
    private final LlmUsageRecordingClient recordingClient = new LlmUsageRecordingClient(fakeLlmClient, llmUsageLogService);

    @AfterEach
    void clearTransactionFlag() {
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void call_성공_토큰과세션사건기록() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respond("서재에는 가지 않았습니다."));

        // when
        LlmResponse response = recordingClient.call(request(), GAME_CONTEXT);

        // then
        assertThat(response.text()).isEqualTo("서재에는 가지 않았습니다.");
        LlmUsageRecord usageRecord = capturedRecords(1).getFirst();
        assertThat(usageRecord).isEqualTo(new LlmUsageRecord(LlmRequestType.NPC_CHAT, FakeLlmClient.MAIN_MODEL, 12L, 3L,
                FakeLlmClient.DEFAULT_INPUT_TOKENS, FakeLlmClient.DEFAULT_OUTPUT_TOKENS, LlmCallStatus.SUCCEEDED, null));
    }

    @Test
    void call_사용량미확인_토큰null() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.respondWithoutUsage("서재에는 가지 않았습니다."));

        // when
        recordingClient.call(request(), GAME_CONTEXT);

        // then
        LlmUsageRecord usageRecord = capturedRecords(1).getFirst();
        assertThat(usageRecord.callStatus()).isEqualTo(LlmCallStatus.SUCCEEDED);
        assertThat(usageRecord.inputTokens()).isNull();
        assertThat(usageRecord.outputTokens()).isNull();
    }

    @Test
    void call_실패와재시도_호출마다기록() {

        // given
        fakeLlmClient.enqueue(FakeLlmScenario.timeout(), FakeLlmScenario.serviceError(), FakeLlmScenario.respond("답"));

        // when
        assertThatThrownBy(() -> recordingClient.call(request(), GAME_CONTEXT)).isInstanceOf(LlmException.class);
        assertThatThrownBy(() -> recordingClient.call(request(), GAME_CONTEXT)).isInstanceOf(LlmException.class);
        recordingClient.call(request(), GAME_CONTEXT);

        // then
        List<LlmUsageRecord> usageRecordList = capturedRecords(3);
        assertThat(usageRecordList).extracting(LlmUsageRecord::callStatus)
                .containsExactly(LlmCallStatus.FAILED, LlmCallStatus.FAILED, LlmCallStatus.SUCCEEDED);
        assertThat(usageRecordList).extracting(LlmUsageRecord::errorCode)
                .containsExactly("LLM_TIMEOUT", "LLM_SERVICE_ERROR", null);
        assertThat(usageRecordList.get(0).inputTokens()).isNull();
        assertThat(usageRecordList.get(0).outputTokens()).isNull();
        assertThat(usageRecordList.get(0).model()).isEqualTo(FakeLlmClient.MAIN_MODEL);
    }

    @Test
    void call_사용량기록실패_호출결과유지() {

        // given
        doThrow(new IllegalStateException("db down")).when(llmUsageLogService).recordUsage(any());

        // when
        LlmResponse response = recordingClient.call(request(), GAME_CONTEXT);

        // then
        assertThat(response.text()).isEqualTo(FakeLlmClient.DEFAULT_TEXT);
    }

    @Test
    void call_트랜잭션안에서호출_거부() {

        // given
        TransactionSynchronizationManager.setActualTransactionActive(true);

        // when & then
        assertThatThrownBy(() -> recordingClient.call(request(), GAME_CONTEXT)).isInstanceOf(IllegalStateException.class);
        assertThat(fakeLlmClient.callCount()).isZero();
        verifyNoInteractions(llmUsageLogService);
    }

    private List<LlmUsageRecord> capturedRecords(int count) {
        ArgumentCaptor<LlmUsageRecord> captor = ArgumentCaptor.forClass(LlmUsageRecord.class);
        verify(llmUsageLogService, times(count)).recordUsage(captor.capture());
        return captor.getAllValues();
    }

    private static LlmRequest request() {
        return new LlmRequest(LlmRequestType.NPC_CHAT, LlmModelTier.MAIN, "가상 사건의 집사로 답한다.",
                List.of(LlmMessage.user("어젯밤 9시에 서재 근처에 있었나요?")), 200);
    }
}
