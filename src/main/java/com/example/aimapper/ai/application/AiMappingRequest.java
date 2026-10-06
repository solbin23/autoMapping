package com.example.aimapper.ai.application;

import com.example.aimapper.matching.domain.FieldReference;
import com.example.aimapper.matching.domain.FieldMatch;
import com.example.aimapper.matching.domain.RuleEvidence;
import java.util.List;

/**
 * AI에 전달할 최소 입력이다. 정규화된 소스 필드와 상위 3개 후보만 포함하며
 * Java 원문이나 전체 스키마는 포함하지 않는다.
 */
public record AiMappingRequest(FieldReference source, List<Candidate> candidates) {
    public AiMappingRequest { candidates = List.copyOf(candidates); }
    public record Candidate(FieldReference target, double score, List<RuleEvidence> evidence) {}
    /** 규칙 기반 매칭 결과에서 AI에 허용할 상위 후보만 추려 요청을 만든다. */
    public static AiMappingRequest from(FieldMatch match) {
        return new AiMappingRequest(match.source(), match.candidates().stream().limit(3)
                .map(c -> new Candidate(c.target(), c.score(), c.evidence())).toList());
    }
}
