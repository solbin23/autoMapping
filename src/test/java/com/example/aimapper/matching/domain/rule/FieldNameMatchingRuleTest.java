package com.example.aimapper.matching.domain.rule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static com.example.aimapper.matching.MatchingFixtures.field;
import static org.assertj.core.api.Assertions.assertThat;

/** 필드명 유사도와 value1 같은 무의미한 이름의 자동 확정 차단을 검증한다. */
class FieldNameMatchingRuleTest {
    private final FieldNameMatchingRule rule = new FieldNameMatchingRule();
    @Test void normalizesCaseAndSeparators() {
        assertThat(rule.evaluate(field("saleAmount", "int", false), field("SALE_AMOUNT", "int", false)).score()).isEqualTo(100);
    }
    @Test void ranksPartialNamesAboveUnrelatedNames() {
        var source = field("saleAmount", "int", false);
        double partial = rule.evaluate(source, field("amount", "int", false)).score();
        assertThat(partial).isBetween(1.0, 99.0).isGreaterThan(rule.evaluate(source, field("xyz", "int", false)).score());
    }
    @ParameterizedTest @ValueSource(strings = {"value1", "FIELD_2", "tmp", "option1", "x1"})
    void blocksGenericNamesOnEitherSide(String name) {
        assertThat(rule.evaluate(field(name, "int", false), field("amount", "int", false)).blocksAutoMatch()).isTrue();
        assertThat(rule.evaluate(field("amount", "int", false), field(name, "int", false)).blocksAutoMatch()).isTrue();
    }
    @Test void keepsMeaningfulNamesWithDigits() {
        assertThat(FieldNameMatchingRule.isMeaningless("addressLine1")).isFalse();
    }
}
