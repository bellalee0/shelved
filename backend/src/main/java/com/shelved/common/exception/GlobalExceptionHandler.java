package com.shelved.common.exception;

import static com.shelved.common.constants.ValidationMessage.INPUT_TYPE_MISMATCH;

import com.shelved.common.dto.CommonResponse;
import com.shelved.common.dto.ErrorData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Path;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestValueException;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.exc.MismatchedInputException;

/**
 * 모든 예외를 CommonResponse.exception(...) 형식으로 변환한다.
 * 필터(Security 등)에서 발생한 예외는 여기까지 오지 않으므로 각 필터의 오류 처리기에서 같은 형식으로 응답한다.
 * 로그에는 오류 코드만 남기고 요청 본문·detail(노트 내용 등)은 남기지 않는다.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final long multipartMaxFileBytes;

    public GlobalExceptionHandler(@Value("${spring.servlet.multipart.max-file-size}") DataSize multipartMaxFileSize) {
        this.multipartMaxFileBytes = multipartMaxFileSize.toBytes();
    }

    // 비즈니스 예외
    @ExceptionHandler(CustomException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleCustomException(CustomException e) {

        log.info("CustomException: {}", e.getErrorCode().name());

        return ResponseEntity.status(e.getErrorCode().getStatus())
                .body(CommonResponse.exception(e.getErrorCode(), e.getDetail()));
    }

    // @Valid @RequestBody 검증 실패
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleMethodArgumentNotValid(MethodArgumentNotValidException e) {

        FieldError fieldError = e.getBindingResult().getFieldError();
        if (fieldError != null) {
            return invalidInput(fieldError.getDefaultMessage(), fieldError.getField());
        }

        String message = e.getBindingResult().getAllErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse(null);

        return invalidInput(message, null);
    }

    // 컨트롤러 메서드 파라미터에 직접 붙인 제약(@Min 등) 위반
    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleHandlerMethodValidation(HandlerMethodValidationException e) {

        ParameterValidationResult result = e.getParameterValidationResults().getFirst();
        MethodParameter parameter = result.getMethodParameter();

        if (parameter.hasParameterAnnotation(RequestBody.class)) {
            if (result instanceof ParameterErrors errors && errors.getFieldError() != null) {
                FieldError fieldError = errors.getFieldError();
                return invalidInput(fieldError.getDefaultMessage(), fieldError.getField());
            }
            return invalidInput(firstMessage(result), null);
        }

        return invalidParameter(resolveParameterName(parameter));
    }

    // @Validated 클래스의 메서드 검증 실패
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleConstraintViolation(ConstraintViolationException e) {

        String field = e.getConstraintViolations().stream()
                .findFirst()
                .map(ConstraintViolation::getPropertyPath)
                .map(GlobalExceptionHandler::lastNodeName)
                .orElse(null);

        return invalidParameter(field);
    }

    // 본문 파싱 실패. 알려진 필드의 타입 불일치(정수 필드에 소수 등)는 INVALID_INPUT으로 구분한다
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleHttpMessageNotReadable(HttpMessageNotReadableException e) {

        if (e.getCause() instanceof MismatchedInputException mismatch && !mismatch.getPath().isEmpty()) {
            return invalidInput(INPUT_TYPE_MISMATCH, toFieldPath(mismatch));
        }

        return error(ErrorCode.INVALID_REQUEST_BODY, null);
    }

    // JSON이 아닌 Content-Type으로 본문을 보낸 경우
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleHttpMediaTypeNotSupported(HttpMediaTypeNotSupportedException e) {
        return error(ErrorCode.INVALID_REQUEST_BODY, null);
    }

    // 경로·쿼리 파라미터 타입 불일치
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleMethodArgumentTypeMismatch(MethodArgumentTypeMismatchException e) {
        return invalidParameter(e.getName());
    }

    // 쿼리 파라미터·헤더·경로 변수 누락
    @ExceptionHandler(MissingRequestValueException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleMissingRequestValue(MissingRequestValueException e) {

        String field = switch (e) {
            case MissingServletRequestParameterException ex -> ex.getParameterName();
            case MissingRequestHeaderException ex -> ex.getHeaderName();
            case MissingPathVariableException ex -> ex.getVariableName();
            default -> null;
        };

        return invalidParameter(field);
    }

    // multipart 파트 누락
    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleMissingServletRequestPart(MissingServletRequestPartException e) {
        return invalidParameter(e.getRequestPartName());
    }

    // 없는 경로
    @ExceptionHandler({NoResourceFoundException.class, NoHandlerFoundException.class})
    public ResponseEntity<CommonResponse<ErrorData>> handleNotFound(Exception e) {
        return error(ErrorCode.API_NOT_FOUND, null);
    }

    // 지원하지 않는 HTTP 메서드
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        return error(ErrorCode.METHOD_NOT_ALLOWED, null);
    }

    // 업로드 크기 초과 (spring.servlet.multipart 한도). 파일 종류별 한도는 각 업로드 API에서 CustomException으로 던진다
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleMaxUploadSizeExceeded(MaxUploadSizeExceededException e) {
        return error(ErrorCode.FILE_TOO_LARGE, Map.of("maxBytes", multipartMaxFileBytes));
    }

    // 그 외 예외. 스택 트레이스·내부 메시지는 서버 로그에만 남기고 응답에 넣지 않는다
    @ExceptionHandler(Exception.class)
    public ResponseEntity<CommonResponse<ErrorData>> handleException(Exception e) {

        log.error("Unhandled exception", e);

        return error(ErrorCode.INTERNAL_SERVER_ERROR, null);
    }

    private ResponseEntity<CommonResponse<ErrorData>> error(ErrorCode errorCode, Object detail) {
        return ResponseEntity.status(errorCode.getStatus())
                .body(CommonResponse.exception(errorCode, detail));
    }

    private ResponseEntity<CommonResponse<ErrorData>> invalidInput(String message, String field) {

        String resolvedMessage = StringUtils.hasText(message) ? message : ErrorCode.INVALID_INPUT.getMessage();

        return ResponseEntity.status(ErrorCode.INVALID_INPUT.getStatus())
                .body(CommonResponse.exception(ErrorCode.INVALID_INPUT, resolvedMessage, fieldDetail(field)));
    }

    private ResponseEntity<CommonResponse<ErrorData>> invalidParameter(String field) {
        return error(ErrorCode.INVALID_PARAMETER, fieldDetail(field));
    }

    private static Map<String, String> fieldDetail(String field) {
        return field == null ? null : Map.of("field", field);
    }

    private static String firstMessage(ParameterValidationResult result) {
        return result.getResolvableErrors().stream()
                .findFirst()
                .map(MessageSourceResolvable::getDefaultMessage)
                .orElse(null);
    }

    // 요청에 쓰이는 이름(@RequestParam("page") 등)을 우선하고, 없으면 Java 파라미터 이름을 쓴다
    private static String resolveParameterName(MethodParameter parameter) {

        String name = null;
        if (parameter.getParameterAnnotation(RequestParam.class) instanceof RequestParam a) {
            name = a.name();
        } else if (parameter.getParameterAnnotation(PathVariable.class) instanceof PathVariable a) {
            name = a.name();
        } else if (parameter.getParameterAnnotation(RequestHeader.class) instanceof RequestHeader a) {
            name = a.name();
        } else if (parameter.getParameterAnnotation(RequestPart.class) instanceof RequestPart a) {
            name = a.name();
        }

        return StringUtils.hasText(name) ? name : parameter.getParameterName();
    }

    private static String lastNodeName(Path path) {

        String name = null;
        for (Path.Node node : path) {
            name = node.getName();
        }

        return name;
    }

    // 예: items[0].name
    private static String toFieldPath(MismatchedInputException e) {
        return e.getPath().stream()
                .map(GlobalExceptionHandler::toPathSegment)
                .collect(Collectors.joining())
                .replaceFirst("^\\.", "");
    }

    private static String toPathSegment(JacksonException.Reference reference) {
        if (reference.getPropertyName() != null) {
            return "." + reference.getPropertyName();
        }
        return "[" + reference.getIndex() + "]";
    }
}
