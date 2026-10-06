package com.example.aimapper.matching.domain.rule;

import com.example.aimapper.matching.domain.*;
import org.springframework.stereotype.Component;
import java.util.Map;
import java.util.Set;

/** 동일 타입, 박싱 타입, 숫자 확대 변환 등 Java 타입 호환성을 평가한다. */
@Component
public class JavaTypeMatchingRule implements MatchingRule {
    private static final Map<String, String> BOXES = Map.of(
            "java.lang.Byte", "byte", "java.lang.Short", "short", "java.lang.Integer", "int",
            "java.lang.Long", "long", "java.lang.Float", "float", "java.lang.Double", "double",
            "java.lang.Character", "char", "java.lang.Boolean", "boolean");
    private static final Map<String, Set<String>> WIDENING = Map.of(
            "byte", Set.of("short", "int", "long", "float", "double"),
            "short", Set.of("int", "long", "float", "double"),
            "char", Set.of("int", "long", "float", "double"),
            "int", Set.of("long", "float", "double"),
            "long", Set.of("float", "double"), "float", Set.of("double"));

    public String id() { return "java-type"; }
    public double weight() { return 25; }

    public RuleScore evaluate(FieldReference source, FieldReference target) {
        var a = source.field();
        var b = target.field();
        if (a.collection() != b.collection()) return blocked(0, "Scalar/collection mismatch");
        String from = BOXES.getOrDefault(a.javaType(), a.javaType());
        String to = BOXES.getOrDefault(b.javaType(), b.javaType());
        double score;
        String reason;
        if (a.javaType().equals(b.javaType())) {
            score = 100; reason = "Identical Java types";
        } else if (from.equals(to)) {
            score = 95; reason = "Primitive/wrapper conversion";
        } else if (WIDENING.getOrDefault(from, Set.of()).contains(to)) {
            // Widening followed by boxing into a different wrapper is not Java assignment-compatible.
            if (BOXES.containsKey(b.javaType())) return blocked(40, "Numeric wrapper conversion requires explicit mapping");
            score = 80; reason = "Java numeric widening: " + a.javaType() + " -> " + b.javaType();
            if ((from.equals("long") && Set.of("float", "double").contains(to))
                    || (from.equals("int") && to.equals("float"))) {
                return blocked(60, reason + "; potential precision loss");
            }
        } else {
            return blocked(0, "Incompatible or unverified Java conversion: " + a.javaType() + " -> " + b.javaType());
        }
        if (a.nullable() && !b.nullable()) return blocked(score * 0.7, reason + "; nullable source to non-null target");
        return RuleScore.scored(score, reason);
    }

    private RuleScore blocked(double score, String reason) { return new RuleScore(score, true, true, reason); }
}
