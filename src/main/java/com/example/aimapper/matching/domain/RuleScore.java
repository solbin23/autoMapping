package com.example.aimapper.matching.domain;

/**
 * 한 규칙의 0~100 점수와 적용 여부, 자동 확정 차단 여부 및 사람이 읽을 수 있는 근거이다.
 */
public record RuleScore(double score, boolean applicable, boolean blocksAutoMatch, String reason) {
    public RuleScore {
        if (!Double.isFinite(score) || score < 0 || score > 100) {
            throw new IllegalArgumentException("Rule score must be between 0 and 100");
        }
    }

    /** 비교 가능한 값이 있어 실제 점수를 산출했을 때 사용한다. */
    public static RuleScore scored(double score, String reason) {
        return new RuleScore(score, true, false, reason);
    }

    /** 주석 누락처럼 해당 규칙을 적용할 자료가 없을 때 사용한다. */
    public static RuleScore absent(String reason) {
        return new RuleScore(0, false, false, reason);
    }
}
