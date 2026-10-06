package com.example.aimapper.execution.application;

/** 승인 계획 또는 입력 데이터 때문에 특정 필드 변환을 수행할 수 없을 때 발생한다. */
public class MappingExecutionException extends RuntimeException {
    public MappingExecutionException(String message) {
        super(message);
    }

    public MappingExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}
