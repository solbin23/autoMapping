package com.example.aimapper.matching.domain.rule;

import com.example.aimapper.matching.domain.*;
import org.springframework.stereotype.Component;
import java.util.Set;
import java.util.stream.Collectors;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.expr.AnnotationExpr;

/** 어노테이션 이름과 속성값의 겹치는 정도를 비교한다. */
@Component
public class AnnotationMatchingRule implements MatchingRule {
    public String id() { return "annotation"; }
    public double weight() { return 5; }
    public RuleScore evaluate(FieldReference source, FieldReference target) {
        Set<String> a = normalize(source), b = normalize(target);
        if (a.isEmpty() && b.isEmpty()) return RuleScore.absent("No annotations to compare");
        return RuleScore.scored(TextSimilarity.overlap(a, b), "Annotation names and attributes overlap: " + a + " -> " + b);
    }

    private Set<String> normalize(FieldReference reference) {
        return reference.field().annotations().stream().map(this::canonical).collect(Collectors.toCollection(java.util.TreeSet::new));
    }

    private String canonical(String value) {
        AnnotationExpr annotation = StaticJavaParser.parseAnnotation(value);
        String name = annotation.getName().getIdentifier();
        if (annotation.isSingleMemberAnnotationExpr()) return name + "(value=" + annotation.asSingleMemberAnnotationExpr().getMemberValue() + ")";
        if (annotation.isNormalAnnotationExpr()) return name + annotation.asNormalAnnotationExpr().getPairs().stream()
                .map(pair -> pair.getNameAsString() + "=" + pair.getValue()).sorted().collect(Collectors.joining(",", "(", ")"));
        return name + "()";
    }
}
