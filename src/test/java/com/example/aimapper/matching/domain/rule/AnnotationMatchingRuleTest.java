package com.example.aimapper.matching.domain.rule;

import org.junit.jupiter.api.Test;
import static com.example.aimapper.matching.MatchingFixtures.described;
import static org.assertj.core.api.Assertions.assertThat;

/** 어노테이션 이름과 속성값 비교 결과를 검증한다. */
class AnnotationMatchingRuleTest {
    private final AnnotationMatchingRule rule = new AnnotationMatchingRule();
    @Test void normalizesQualificationAttributeOrderAndShorthand() {
        assertThat(rule.evaluate(described(null, "@NotNull", "@Size(min=1,max=10)", "@Label(\"price\")"),
                described(null, "@jakarta.validation.constraints.NotNull", "@Size(max = 10, min = 1)", "@Label(value=\"price\")")).score()).isEqualTo(100);
    }
    @Test void preservesAttributeValuesIncludingStringWhitespace() {
        assertThat(rule.evaluate(described(null, "@Label(\"a b\")"), described(null, "@Label(\"ab\")")).score()).isZero();
        assertThat(rule.evaluate(described(null, "@Size(max=10)"), described(null, "@Size(max=20)")).score()).isZero();
    }
    @Test void missingAnnotationsDoNotIncreaseScore() {
        assertThat(rule.evaluate(described(null), described(null)).applicable()).isFalse();
        assertThat(rule.evaluate(described(null, "@NotNull"), described(null)).score()).isZero();
    }
}
