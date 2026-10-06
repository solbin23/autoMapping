package com.example.aimapper.ai.domain;

/** AI 호출의 모델명, 토큰 사용량, 응답시간, 성공 여부와 오류 코드를 기록한다. */
public record AiCallRecord(String model, Integer inputTokens, Integer outputTokens, Integer totalTokens,
                           long responseTimeMs, boolean success, String errorCode) {}
