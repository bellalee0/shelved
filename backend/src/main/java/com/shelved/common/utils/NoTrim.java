package com.shelved.common.utils;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 요청 본문 문자열의 공통 앞뒤 공백 제거에서 제외할 필드에 붙인다.
 * 공백·줄바꿈을 그대로 보존해야 하는 필드(예: 개인 노트 content)에만 사용한다.
 *
 * <pre>
 * public record NoteUpdateRequest(
 *         &#64;NoTrim
 *         &#64;Size(max = 5000, message = ...)
 *         String content,
 *         Long revision
 * ) {
 * }
 * </pre>
 */
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoTrim {
}
