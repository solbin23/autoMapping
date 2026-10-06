package com.example.aimapper.common.presentation;

import java.time.Instant;

/** 모든 잘못된 API 요청을 동일한 형태로 반환하기 위한 오류 응답 모델이다. */
public record ApiError(Instant timestamp, int status, String error, String message, String path) {
}
