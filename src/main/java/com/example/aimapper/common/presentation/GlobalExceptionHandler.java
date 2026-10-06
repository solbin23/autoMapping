package com.example.aimapper.common.presentation;

import com.example.aimapper.generation.application.MappingProjectNotFoundException;
import com.example.aimapper.generation.application.NoApprovedMappingsException;
import com.example.aimapper.generation.application.ProjectRevisionConflictException;
import com.example.aimapper.schema.infrastructure.SchemaParseException;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;

/** 예외 종류를 구체적인 HTTP 상태와 고정 오류 코드가 포함된 공통 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** 존재하지 않는 매핑 프로젝트는 404로 반환한다. */
    @ExceptionHandler(MappingProjectNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(MappingProjectNotFoundException exception,
                                                    HttpServletRequest request) {
        return error(HttpStatus.NOT_FOUND, ApiErrorCode.MAPPING_PROJECT_NOT_FOUND, exception, request);
    }

    /** 오래된 revision을 사용한 변경·생성·내보내기 요청은 409로 반환한다. */
    @ExceptionHandler(ProjectRevisionConflictException.class)
    public ResponseEntity<ApiError> handleConflict(ProjectRevisionConflictException exception,
                                                    HttpServletRequest request) {
        return error(HttpStatus.CONFLICT, ApiErrorCode.PROJECT_REVISION_CONFLICT, exception, request);
    }

    /** 문법은 유효하지만 승인 매핑이 없어 처리할 수 없는 생성 요청은 422로 반환한다. */
    @ExceptionHandler(NoApprovedMappingsException.class)
    public ResponseEntity<ApiError> handleUnprocessable(NoApprovedMappingsException exception,
                                                        HttpServletRequest request) {
        return error(HttpStatus.UNPROCESSABLE_ENTITY, ApiErrorCode.NO_APPROVED_MAPPINGS, exception, request);
    }

    /** Java 소스 구문 오류는 일반 입력 오류와 구분되는 400 코드로 반환한다. */
    @ExceptionHandler(SchemaParseException.class)
    public ResponseEntity<ApiError> handleSchemaParse(SchemaParseException exception,
                                                      HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.SCHEMA_PARSE_ERROR, exception, request);
    }

    /** 필수 multipart 또는 query parameter가 없으면 400으로 반환한다. */
    @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<ApiError> handleMissingValue(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.MISSING_REQUEST_VALUE, exception, request);
    }

    /** 읽을 수 없는 JSON과 Bean Validation 실패는 400으로 반환한다. */
    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    public ResponseEntity<ApiError> handleMalformedRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.MALFORMED_REQUEST, exception, request);
    }

    /** 파일명, 매핑 조합, Java 식별자 등 애플리케이션 입력 검증 실패는 400으로 반환한다. */
    @ExceptionHandler({IllegalArgumentException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        return error(HttpStatus.BAD_REQUEST, ApiErrorCode.INVALID_REQUEST, exception, request);
    }

    /** 설정된 multipart 용량을 넘은 업로드는 413으로 반환한다. */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiError> handleUploadTooLarge(MaxUploadSizeExceededException exception,
                                                         HttpServletRequest request) {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, ApiErrorCode.UPLOAD_TOO_LARGE, exception, request);
    }

    /** 지원하지 않는 Content-Type 요청은 415로 반환한다. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> handleUnsupportedMediaType(HttpMediaTypeNotSupportedException exception,
                                                               HttpServletRequest request) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ApiErrorCode.UNSUPPORTED_MEDIA_TYPE, exception, request);
    }

    /** 예상하지 못한 오류는 상세 구현 정보를 숨기고 요청 추적용 서버 로그를 남긴다. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleInternalError(Exception exception, HttpServletRequest request) {
        log.error("Unhandled API error path={}", request.getRequestURI(), exception);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, ApiErrorCode.INTERNAL_ERROR,
                "Unexpected server error", request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, ApiErrorCode code, Exception exception,
                                           HttpServletRequest request) {
        String message = exception.getMessage() == null || exception.getMessage().isBlank()
                ? status.getReasonPhrase() : exception.getMessage();
        return error(status, code, message, request);
    }

    private ResponseEntity<ApiError> error(HttpStatus status, ApiErrorCode code, String message,
                                           HttpServletRequest request) {
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), code, message, request.getRequestURI()));
    }
}
