package com.example.aimapper.matching.domain.rule;

import org.junit.jupiter.api.Test;
import static com.example.aimapper.matching.MatchingFixtures.field;
import static org.assertj.core.api.Assertions.assertThat;

/** 중첩 경로 깊이와 컬렉션 구조에 따른 유사도 계산을 검증한다. */
class NestedStructureMatchingRuleTest {
    private final NestedStructureMatchingRule rule = new NestedStructureMatchingRule();
    @Test void matchesParentStructureIndependentlyOfLeafName() {
        assertThat(rule.evaluate(field("options[].price", "int", false), field("options[].amount", "int", false)).score()).isEqualTo(100);
    }
    @Test void distinguishesParentNames() {
        var source = field("billing.amount", "int", false);
        assertThat(rule.evaluate(source, field("billing.total", "int", false)).score())
                .isGreaterThan(rule.evaluate(source, field("shipping.total", "int", false)).score());
    }
    @Test void blocksChangedDepthAndCollectionPosition() {
        assertThat(rule.evaluate(field("amount", "int", false), field("price.amount", "int", false)).blocksAutoMatch()).isTrue();
        assertThat(rule.evaluate(field("items[].amount", "int", false), field("items.amount[]", "int", false)).blocksAutoMatch()).isTrue();
    }
}
