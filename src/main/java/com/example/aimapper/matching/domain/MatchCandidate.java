package com.example.aimapper.matching.domain;

import java.util.List;

/** 하나의 TO-BE 후보에 대한 종합 점수와 규칙별 계산 근거이다. */
public record MatchCandidate(FieldReference target, double score, List<RuleEvidence> evidence) {
    public MatchCandidate { evidence = List.copyOf(evidence); }
}
