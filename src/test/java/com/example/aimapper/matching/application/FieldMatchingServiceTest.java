package com.example.aimapper.matching.application;

import com.example.aimapper.matching.domain.*;
import com.example.aimapper.matching.domain.rule.*;
import com.example.aimapper.schema.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.example.aimapper.matching.MatchingFixtures.field;
import static org.assertj.core.api.Assertions.*;

/** 규칙 점수 합산, 후보 정렬, 상위 3개 제한 및 자동 확정 조건을 검증한다. */
class FieldMatchingServiceTest {
    private final List<MatchingRule> rules = List.of(new FieldNameMatchingRule(), new JavaTypeMatchingRule(),
            new NestedStructureMatchingRule(), new CommentMatchingRule(), new AnnotationMatchingRule());
    private final FieldMatchingService service = new FieldMatchingService(rules);

    @Test void returnsThreeRankedCandidatesWithEvidenceForEverySource() {
        var result = service.match(snapshot(SchemaSide.AS_IS, "Old", "amount", "name"),
                snapshot(SchemaSide.TO_BE, "New", "xyz", "amount", "name", "saleAmount"));
        assertThat(result).hasSize(2);
        assertThat(result.getFirst().candidates()).hasSize(3);
        assertThat(result.getFirst().candidates().getFirst().target().field().path()).isEqualTo("amount");
        assertThat(result.getFirst().candidates()).extracting(MatchCandidate::score).isSortedAccordingTo(java.util.Comparator.reverseOrder());
        assertThat(result.getFirst().candidates().getFirst().evidence()).hasSize(5).allSatisfy(e -> assertThat(e.result().reason()).isNotBlank());
        assertThat(result.getFirst().status()).isEqualTo(FieldMatch.Status.AUTO_MATCHED);
    }
    @Test void genericNameNeverAutoMatchesEvenWithPerfectScore() {
        var result = service.match(snapshot(SchemaSide.AS_IS, "Old", "value1"), snapshot(SchemaSide.TO_BE, "New", "value1")).getFirst();
        assertThat(result.candidates().getFirst().score()).isEqualTo(100);
        assertThat(result.status()).isEqualTo(FieldMatch.Status.REVIEW_REQUIRED);
    }
    @Test void tiesAreStableAcrossClassesAndRequireReview() {
        var targets = new SchemaSnapshot(SchemaSide.TO_BE, List.of(
                snapshot(SchemaSide.TO_BE, "Z", "amount").classes().getFirst(),
                snapshot(SchemaSide.TO_BE, "A", "amount").classes().getFirst()));
        var result = service.match(snapshot(SchemaSide.AS_IS, "Old", "amount"), targets).getFirst();
        assertThat(result.status()).isEqualTo(FieldMatch.Status.REVIEW_REQUIRED);
        assertThat(result.candidates()).extracting(c -> c.target().className()).containsExactly("A", "Z");
    }
    @Test void emptyTargetsAndSourcesAreSupported() {
        assertThat(service.match(snapshot(SchemaSide.AS_IS, "Old", "amount"), snapshot(SchemaSide.TO_BE, "New")))
                .singleElement().satisfies(m -> {
                    assertThat(m.status()).isEqualTo(FieldMatch.Status.NO_CANDIDATE);
                    assertThat(m.candidates()).isEmpty();
                });
        assertThat(service.match(snapshot(SchemaSide.AS_IS, "Old"), snapshot(SchemaSide.TO_BE, "New", "amount"))).isEmpty();
    }
    @Test void newRuleParticipatesWithoutChangingService() {
        MatchingRule custom = new MatchingRule() {
            public String id() { return "custom"; }
            public double weight() { return 100; }
            public RuleScore evaluate(FieldReference a, FieldReference b) { return new RuleScore(100, true, true, "Custom review"); }
        };
        var extended = new java.util.ArrayList<>(rules);
        extended.add(custom);
        var result = new FieldMatchingService(extended).match(snapshot(SchemaSide.AS_IS, "Old", "amount"),
                snapshot(SchemaSide.TO_BE, "New", "amount")).getFirst();
        assertThat(result.candidates().getFirst().evidence()).hasSize(6);
        assertThat(result.status()).isEqualTo(FieldMatch.Status.REVIEW_REQUIRED);
    }
    @Test void ignoresUnavailableEvidenceInWeightedAverage() {
        var result = service.match(snapshot(SchemaSide.AS_IS, "Old", "amount"), snapshot(SchemaSide.TO_BE, "New", "amount")).getFirst();
        assertThat(result.candidates().getFirst().score()).isEqualTo(100);
    }
    @Test void rejectsDuplicateRules() {
        assertThatThrownBy(() -> new FieldMatchingService(List.of(new FieldNameMatchingRule(), new FieldNameMatchingRule())))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test void incompatibleTypesAndWeakNamesRequireReview() {
        var target = new SchemaSnapshot(SchemaSide.TO_BE, List.of(new SchemaClass("", "New", "New",
                List.of(field("amount", "java.lang.String", true).field()))));
        assertThat(service.match(snapshot(SchemaSide.AS_IS, "Old", "amount"), target).getFirst().status())
                .isEqualTo(FieldMatch.Status.REVIEW_REQUIRED);
        assertThat(service.match(snapshot(SchemaSide.AS_IS, "Old", "amount"),
                snapshot(SchemaSide.TO_BE, "New", "address")).getFirst().status()).isEqualTo(FieldMatch.Status.REVIEW_REQUIRED);
    }
    @Test void calculatesWeightedScoresWithoutMissingMetadata() {
        var target = new SchemaSnapshot(SchemaSide.TO_BE, List.of(new SchemaClass("", "New", "New",
                List.of(field("amount", "long", false).field()))));
        // (40 * 100 + 25 * 80 + 15 * 100) / 80 = 93.75
        assertThat(service.match(snapshot(SchemaSide.AS_IS, "Old", "amount"), target)
                .getFirst().candidates().getFirst().score()).isEqualTo(93.75);
    }
    private SchemaSnapshot snapshot(SchemaSide side, String owner, String... names) {
        return new SchemaSnapshot(side, List.of(new SchemaClass("", owner, owner,
                java.util.Arrays.stream(names).map(name -> field(name, "int", false).field()).toList())));
    }
}
