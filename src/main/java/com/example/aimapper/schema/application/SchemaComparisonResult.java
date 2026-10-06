package com.example.aimapper.schema.application;

import com.example.aimapper.schema.domain.SchemaSnapshot;
import com.example.aimapper.matching.domain.FieldMatch;
import java.util.List;
import com.example.aimapper.ai.domain.AiFieldResult;

/**
 * 분석된 양쪽 스키마, 규칙 기반 매칭 결과, AI 보조 제안을 함께 반환하는 결과 모델이다.
 */
public record SchemaComparisonResult(SchemaSnapshot asIs, SchemaSnapshot toBe, List<FieldMatch> matches,
                                     List<AiFieldResult> aiSuggestions) {
    public SchemaComparisonResult {
        matches = List.copyOf(matches);
        aiSuggestions = List.copyOf(aiSuggestions);
    }
}
