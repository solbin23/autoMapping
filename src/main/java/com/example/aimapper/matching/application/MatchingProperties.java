package com.example.aimapper.matching.application;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * 규칙 기반 자동 매칭 여부를 결정하는 운영 설정이다.
 * 점수와 후보 간 점수 차이는 모두 0점부터 100점 사이의 값만 허용한다.
 */
@ConfigurationProperties(prefix = "mapping.matching")
public record MatchingProperties(
        @DefaultValue("85") double autoMatchMinScore,
        @DefaultValue("10") double minimumScoreGap) {

    /** 설정 오류가 있는 상태로 애플리케이션이 시작되지 않도록 값의 범위를 검증한다. */
    public MatchingProperties {
        validate("auto-match-min-score", autoMatchMinScore);
        validate("minimum-score-gap", minimumScoreGap);
    }

    private static void validate(String name, double value) {
        if (!Double.isFinite(value) || value < 0 || value > 100) {
            throw new IllegalArgumentException("mapping.matching." + name + " must be between 0 and 100");
        }
    }
}
