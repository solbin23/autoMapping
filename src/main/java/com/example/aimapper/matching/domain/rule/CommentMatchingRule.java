package com.example.aimapper.matching.domain.rule;

import com.example.aimapper.matching.domain.*;
import org.springframework.stereotype.Component;

/** 양쪽 필드 주석의 텍스트 유사도를 평가하며, 한쪽이라도 없으면 점수 계산에서 제외한다. */
@Component
public class CommentMatchingRule implements MatchingRule {
    public String id() { return "comment"; }
    public double weight() { return 15; }
    public RuleScore evaluate(FieldReference source, FieldReference target) {
        String a = source.field().description(), b = target.field().description();
        if (a == null || b == null || a.isBlank() || b.isBlank()) return RuleScore.absent("Comment missing on one or both fields");
        return RuleScore.scored(TextSimilarity.compare(a, b), "Comment text similarity: " + a + " -> " + b);
    }
}
