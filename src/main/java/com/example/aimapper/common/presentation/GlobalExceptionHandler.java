package com.example.aimapper.common.presentation;

import com.example.aimapper.schema.infrastructure.SchemaParseException;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

import java.time.Instant;

/** 입력 검증 및 소스 분석 오류를 HTTP 400 공통 응답으로 변환한다. */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, SchemaParseException.class,
            MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
    /** 클라이언트가 수정할 수 있는 요청 오류에 발생 시간과 경로를 붙여 반환한다. */
    public ResponseEntity<ApiError> handleBadRequest(Exception exception, HttpServletRequest request) {
        HttpStatus status = HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status).body(new ApiError(
                Instant.now(), status.value(), status.getReasonPhrase(), exception.getMessage(), request.getRequestURI()));
    }
}
