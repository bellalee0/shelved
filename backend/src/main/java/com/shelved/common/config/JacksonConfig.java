package com.shelved.common.config;

import com.shelved.common.utils.TrimStringDeserializer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.JacksonModule;
import tools.jackson.databind.module.SimpleModule;

@Configuration
public class JacksonConfig {

    /**
     * 요청 본문의 문자열 앞뒤 공백 제거 (docs/api/README.md 공통 규칙)
     * 원문을 보존해야 하는 필드는 @NoTrim을 붙인다.
     */
    @Bean
    public JacksonModule stringTrimModule() {
        SimpleModule module = new SimpleModule("StringTrimModule");
        module.addDeserializer(String.class, new TrimStringDeserializer());
        return module;
    }
}
