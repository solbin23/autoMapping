package com.example.aimapper.matching.domain;

/**
 * 필드 쌍을 평가하는 확장 지점이다. 새 구현체를 Spring Bean으로 등록하면 자동으로 합산에 참여한다.
 * 각 규칙의 점수는 0~100 범위로 정규화해야 한다.
 */
public interface MatchingRule {
    /** 로그와 근거 표시에 사용할 고유 규칙 ID이다. */
    String id();

    /** 전체 후보 점수에서 이 규칙이 차지하는 양의 가중치이다. */
    double weight();

    /** 하나의 AS-IS/TO-BE 필드 쌍을 평가한다. */
    RuleScore evaluate(FieldReference source, FieldReference target);
}
