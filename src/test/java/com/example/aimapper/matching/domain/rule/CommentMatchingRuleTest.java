package com.example.aimapper.matching.domain.rule;

import org.junit.jupiter.api.Test;
import static com.example.aimapper.matching.MatchingFixtures.described;
import static org.assertj.core.api.Assertions.assertThat;

/** 주석의 유사도 계산과 주석 누락 시 규칙 제외 처리를 검증한다. */
class CommentMatchingRuleTest {
    private final CommentMatchingRule rule = new CommentMatchingRule();
    @Test void missingCommentsAreNotPositiveEvidence() {
        assertThat(rule.evaluate(described(null), described(null)).applicable()).isFalse();
        assertThat(rule.evaluate(described(" "), described("Amount")).applicable()).isFalse();
    }
    @Test void comparesKoreanAndEnglishText() {
        assertThat(rule.evaluate(described("판매 금액"), described("판매 금액")).score()).isEqualTo(100);
        assertThat(rule.evaluate(described("Sale amount"), described("amount sale")).score()).isEqualTo(100);
        assertThat(rule.evaluate(described("판매 금액"), described("주소")).score()).isLessThan(50);
    }
}
