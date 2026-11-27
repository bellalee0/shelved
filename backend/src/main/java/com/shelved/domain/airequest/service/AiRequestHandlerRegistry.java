package com.shelved.domain.airequest.service;

import com.shelved.common.enums.AiRequestType;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.stereotype.Component;

/**
 * 빈으로 등록된 유형별 AiRequestHandler. 실행 중인 핸들러가 없는 복구 경로(AiRequestTransactionService.failProcessing)가
 * 유형별 실패 후처리(onFailed)를 찾을 때 쓴다. 유형당 핸들러 빈은 하나여야 한다
 */
@Component
public class AiRequestHandlerRegistry {

    private final Map<AiRequestType, AiRequestHandler<?>> handlerByType = new EnumMap<>(AiRequestType.class);

    public AiRequestHandlerRegistry(List<AiRequestHandler<?>> handlerList) {
        for (AiRequestHandler<?> handler : handlerList) {
            AiRequestHandler<?> previous = handlerByType.put(handler.type(), handler);
            if (previous != null) {
                throw new IllegalStateException("같은 유형의 AI 요청 핸들러가 둘 이상입니다. type=" + handler.type());
            }
        }
    }

    public Optional<AiRequestHandler<?>> find(AiRequestType type) {
        return Optional.ofNullable(handlerByType.get(type));
    }
}
