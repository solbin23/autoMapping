package com.example.aimapper.common.presentation;

import java.time.Instant;

/** 모든 API 오류를 동일한 형태로 반환하기 위한 오류 응답 모델이다. */
public record ApiError(Instant timestamp, int status, String error, ApiErrorCode code, String message, String path) {
}
