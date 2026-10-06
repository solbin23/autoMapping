package com.example.aimapper.matching.application;

import com.example.aimapper.matching.domain.*;
import com.example.aimapper.matching.domain.rule.FieldNameMatchingRule;
import com.example.aimapper.schema.domain.SchemaSnapshot;
import org.springframework.stereotype.Service;
import java.util.Comparator;
import java.util.List;

/**
 * 모든 AS-IS 필드를 모든 TO-BE 필드와 비교하고 등록된 규칙의 가중 점수를 합산한다.
 * 각 소스 필드에는 점수가 높은 후보를 최대 3개까지 남긴다.
 */
@Service
public class FieldMatchingService {
    private final List<MatchingRule> rules;

    public FieldMatchingService(List<MatchingRule> rules) {
        if (rules.isEmpty() || rules.stream().anyMatch(r -> !Double.isFinite(r.weight()) || r.weight() <= 0)
                || rules.stream().map(MatchingRule::id).distinct().count() != rules.size()) {
            throw new IllegalArgumentException("Matching rules require unique IDs and positive finite weights");
        }
        this.rules = rules.stream().sorted(Comparator.comparing(MatchingRule::id)).toList();
    }

    /** 두 스키마의 필드를 비교해 자동 확정 가능 여부와 검토 후보를 반환한다. */
    public List<FieldMatch> match(SchemaSnapshot asIs, SchemaSnapshot toBe) {
        List<FieldReference> targets = fields(toBe);
        return fields(asIs).stream().map(source -> match(source, targets)).toList();
    }

    private List<FieldReference> fields(SchemaSnapshot snapshot) {
        return snapshot.classes().stream().flatMap(owner -> owner.fields().stream()
                .map(field -> new FieldReference(owner.qualifiedName(), field))).toList();
    }

    private FieldMatch match(FieldReference source, List<FieldReference> targets) {
        List<MatchCandidate> candidates = targets.stream().map(target -> score(source, target))
                .sorted(Comparator.comparingDouble(MatchCandidate::score).reversed()
                        .thenComparing(candidate -> candidate.target().className())
                        .thenComparing(candidate -> candidate.target().field().path()))
                .limit(3).toList();
        if (candidates.isEmpty()) return new FieldMatch(source, FieldMatch.Status.NO_CANDIDATE, "No TO-BE fields", candidates);
        MatchCandidate best = candidates.getFirst();
        String reason = null;
        if (FieldNameMatchingRule.isMeaningless(source.field().fieldName())
                || FieldNameMatchingRule.isMeaningless(best.target().field().fieldName())) {
            reason = "Generic field name requires manual review";
        } else if (best.evidence().stream().anyMatch(e -> e.result().blocksAutoMatch())) {
            reason = "One or more rules require manual review";
        } else if (best.score() < 85) {
            reason = "Best candidate score is below 85";
        } else if (candidates.size() > 1 && best.score() - candidates.get(1).score() < 10) {
            reason = "Top candidates differ by less than 10 points";
        }
        return new FieldMatch(source, reason == null ? FieldMatch.Status.AUTO_MATCHED : FieldMatch.Status.REVIEW_REQUIRED,
                reason == null ? "Score >= 85, margin >= 10 and no review blockers" : reason, candidates);
    }

    private MatchCandidate score(FieldReference source, FieldReference target) {
        List<RuleEvidence> evidence = rules.stream()
                .map(rule -> new RuleEvidence(rule.id(), rule.weight(), rule.evaluate(source, target))).toList();
        double weight = evidence.stream().filter(e -> e.result().applicable()).mapToDouble(RuleEvidence::weight).sum();
        double total = evidence.stream().filter(e -> e.result().applicable())
                .mapToDouble(e -> e.weight() * e.result().score()).sum();
        double score = weight == 0 ? 0 : Math.round(total / weight * 100.0) / 100.0;
        return new MatchCandidate(target, score, evidence);
    }
}
