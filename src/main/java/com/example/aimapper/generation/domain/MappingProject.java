package com.example.aimapper.generation.domain;

import com.example.aimapper.schema.application.SchemaComparisonResult;
import java.util.List;
import java.util.UUID;

/** 규칙·AI 추천과 사용자의 독립된 최종 결정 및 낙관적 잠금 revision을 보관한다. */
public record MappingProject(UUID id, long revision, SchemaComparisonResult recommendations, List<MappingDecision> decisions) {
    public MappingProject { decisions = List.copyOf(decisions); }
}
