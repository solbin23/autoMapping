package com.example.aimapper.ai.domain;

/** 하나의 소스 필드에 대한 AI 제안과 호출 관측 정보를 함께 반환한다. */
public record AiFieldResult(AiMappingSuggestion.Target source, AiMappingSuggestion suggestion, AiCallRecord call) {}
