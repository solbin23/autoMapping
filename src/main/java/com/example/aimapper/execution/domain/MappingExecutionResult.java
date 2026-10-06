package com.example.aimapper.execution.domain;

import com.fasterxml.jackson.databind.JsonNode;

import java.util.UUID;

/** 승인된 매핑 계획으로 실제 JSON을 변환한 결과와 적용된 매핑 수이다. */
public record MappingExecutionResult(
        UUID projectId,
        long revision,
        String sourceClass,
        String targetClass,
        JsonNode targetData,
        int appliedMappings
) {
}
