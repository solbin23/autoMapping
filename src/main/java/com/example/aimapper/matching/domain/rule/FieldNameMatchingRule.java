package com.example.aimapper.matching.domain.rule;

import com.example.aimapper.matching.domain.*;
import org.springframework.stereotype.Component;

/**
 * 정규화된 필드명 문자열 유사도를 평가한다.
 * value1처럼 의미가 약한 이름이 포함되면 높은 점수여도 자동 확정을 차단한다.
 */
@Component
public class FieldNameMatchingRule implements MatchingRule {
    public String id() { return "field-name"; }
    public double weight() { return 40; }

    /** 이름만으로 업무 의미를 추론하면 위험한 범용·순번 필드인지 판별한다. */
    public static boolean isMeaningless(String name) {
        return TextSimilarity.normalize(name).matches("(?:value|val|field|data|temp|tmp|attr|attribute|col|column|option|param|arg|var)\\d*|[a-z]\\d*");
    }

    public RuleScore evaluate(FieldReference source, FieldReference target) {
        String a = source.field().fieldName(), b = target.field().fieldName();
        boolean ambiguous = isMeaningless(a) || isMeaningless(b);
        return new RuleScore(TextSimilarity.compare(a, b), true, ambiguous,
                ambiguous ? "Generic field name requires manual review: " + a + " -> " + b
                        : "Normalized name/edit/token similarity: " + a + " -> " + b);
    }
}
