package com.example.aimapper.matching.domain.rule;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import static com.example.aimapper.matching.MatchingFixtures.field;
import static org.assertj.core.api.Assertions.assertThat;

/** Java 동일 타입, 박싱 및 숫자 확대 변환 호환성 점수를 검증한다. */
class JavaTypeMatchingRuleTest {
    private final JavaTypeMatchingRule rule = new JavaTypeMatchingRule();
    @ParameterizedTest
    @CsvSource({"int,int,100", "int,java.lang.Integer,95", "java.lang.Integer,int,95", "int,long,80", "char,int,80", "float,double,80"})
    void supportsDirectionalSafeConversions(String from, String to, double score) {
        var result = rule.evaluate(field("amount", from, false), field("amount", to, false));
        assertThat(result.score()).isEqualTo(score);
        assertThat(result.blocksAutoMatch()).isFalse();
    }
    @ParameterizedTest
    @CsvSource({"long,int", "byte,char", "java.lang.String,int", "a.Money,b.Money", "int,float", "long,double", "java.lang.Integer,java.lang.Long"})
    void blocksUnsafeOrUnknownConversions(String from, String to) {
        assertThat(rule.evaluate(field("amount", from, false), field("amount", to, false)).blocksAutoMatch()).isTrue();
    }
    @Test void blocksNullableToRequired() {
        assertThat(rule.evaluate(field("amount", "java.lang.Integer", true), field("amount", "int", false)).blocksAutoMatch()).isTrue();
    }
    @Test void blocksScalarCollectionMismatch() {
        var result = rule.evaluate(field("amount", "int", false), field("amount[]", "int", false));
        assertThat(result.score()).isZero();
        assertThat(result.blocksAutoMatch()).isTrue();
    }
}
