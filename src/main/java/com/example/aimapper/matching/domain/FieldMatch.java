package com.example.aimapper.matching.domain;

import java.util.List;

/**
 * 한 AS-IS 필드에 대한 상위 후보와 규칙 기반 판정 상태를 나타낸다.
 * {@code AUTO_MATCHED}일 때만 첫 후보를 자동 매칭으로 보며 나머지는 언제나 추천 후보이다.
 */
public record FieldMatch(FieldReference source, Status status, String reason, List<MatchCandidate> candidates) {
    public enum Status { AUTO_MATCHED, REVIEW_REQUIRED, NO_CANDIDATE }
    public FieldMatch { candidates = List.copyOf(candidates); }
}
