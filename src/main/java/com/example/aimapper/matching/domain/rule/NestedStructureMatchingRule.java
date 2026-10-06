package com.example.aimapper.matching.domain.rule;

import com.example.aimapper.matching.domain.*;
import org.springframework.stereotype.Component;

/** 필드 경로의 깊이, 부모 이름 및 컬렉션 위치를 비교해 중첩 구조 유사도를 평가한다. */
@Component
public class NestedStructureMatchingRule implements MatchingRule {
    public String id() { return "nested-structure"; }
    public double weight() { return 15; }

    public RuleScore evaluate(FieldReference source, FieldReference target) {
        String[] a = source.field().path().split("\\."), b = target.field().path().split("\\.");
        boolean shapeMatches = a.length == b.length;
        double parents = 0;
        for (int i = 0; i < Math.min(a.length, b.length); i++) {
            if (a[i].contains("[]") != b[i].contains("[]")) shapeMatches = false;
            if (i < Math.min(a.length, b.length) - 1) parents += TextSimilarity.compare(a[i], b[i]);
        }
        int depth = Math.max(a.length, b.length) - 1;
        double parentScore = depth == 0 ? 100 : parents / depth;
        double depthScore = 100.0 * Math.min(a.length, b.length) / Math.max(a.length, b.length);
        double score = 0.4 * depthScore + 0.4 * parentScore + (shapeMatches ? 20 : 0);
        return new RuleScore(score, true, !shapeMatches,
                "Path depth/parent names/collection positions: " + source.field().path() + " -> " + target.field().path()
                        + (shapeMatches ? "" : "; structural transformation requires review"));
    }
}
