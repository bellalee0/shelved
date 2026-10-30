package com.shelved.common.utils;

import tools.jackson.core.JsonParser;
import tools.jackson.databind.BeanProperty;
import tools.jackson.databind.DeserializationContext;
import tools.jackson.databind.ValueDeserializer;
import tools.jackson.databind.deser.jdk.StringDeserializer;
import tools.jackson.databind.deser.std.StdScalarDeserializer;

/**
 * JSON 문자열의 앞뒤 공백(전각 공백 등 유니코드 공백 포함)을 제거한다.
 * 검증(@Valid)은 역직렬화 뒤에 실행되므로 공백만 있는 값은 빈 문자열로 검증된다.
 * {@link NoTrim}이 붙은 프로퍼티는 기본 역직렬화기를 그대로 사용한다.
 */
public class TrimStringDeserializer extends StdScalarDeserializer<String> {

    public TrimStringDeserializer() {
        super(String.class);
    }

    @Override
    public String deserialize(JsonParser p, DeserializationContext ctxt) {
        String value = StringDeserializer.instance.deserialize(p, ctxt);
        return value == null ? null : value.strip();
    }

    @Override
    public ValueDeserializer<?> createContextual(DeserializationContext ctxt, BeanProperty property) {
        if (property != null && property.getAnnotation(NoTrim.class) != null) {
            return StringDeserializer.instance;
        }
        return this;
    }
}
