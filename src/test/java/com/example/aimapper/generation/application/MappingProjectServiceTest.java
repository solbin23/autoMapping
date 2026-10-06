package com.example.aimapper.generation.application;

import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.generation.domain.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static com.example.aimapper.generation.GenerationFixtures.*;
import static com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType.*;
import static com.example.aimapper.generation.domain.MappingDecision.Status.*;
import static org.assertj.core.api.Assertions.*;

/** 추천과 결정의 분리, 상태 전환 검증 및 revision 충돌 처리를 검증한다. */
class MappingProjectServiceTest {
    private final MappingProjectService service = projects();
    private MappingProject create() {
        return service.create(List.of(source("Old.java", "class Old { int amount; }")), List.of(source("New.java", "class New { int amount; int total; }")));
    }
    @Test void recommendationsAndDecisionsRemainIndependentAcrossAllStates() {
        var original = create();
        assertThat(original.decisions().getFirst().status()).isEqualTo(UNMAPPED);
        var source = new Target("Old", "amount");
        var modified = service.decide(original.id(), 0, new MappingDecision(source, MODIFIED, new Target("New", "total"), DIRECT));
        assertThat(modified.decisions().getFirst().status()).isEqualTo(MODIFIED);
        var approved = service.decide(original.id(), 1, new MappingDecision(source, APPROVED, new Target("New", "total"), DIRECT));
        assertThat(approved.decisions().getFirst().target().path()).isEqualTo("total");
        var rejected = service.decide(original.id(), 2, new MappingDecision(source, REJECTED, null, null));
        assertThat(rejected.decisions().getFirst().status()).isEqualTo(REJECTED);
        var unmapped = service.decide(original.id(), 3, new MappingDecision(source, UNMAPPED, null, null));
        assertThat(unmapped.recommendations()).isSameAs(original.recommendations());
        assertThat(unmapped.revision()).isEqualTo(4);
        assertThat(original.decisions().getFirst().status()).isEqualTo(UNMAPPED);
    }
    @Test void rejectsStaleUnknownAndIncompleteDecisions() {
        var project = create();
        var source = new Target("Old", "amount");
        assertThatThrownBy(() -> service.decide(project.id(), 1, new MappingDecision(source, APPROVED, new Target("New", "amount"), DIRECT)))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("revision");
        assertThatThrownBy(() -> service.decide(project.id(), 0, new MappingDecision(source, APPROVED, null, DIRECT))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decide(project.id(), 0, new MappingDecision(source, MODIFIED, new Target("New", "missing"), DIRECT))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decide(project.id(), 0, new MappingDecision(new Target("Other", "amount"), UNMAPPED, null, null))).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.decide(project.id(), 0, new MappingDecision(source, REJECTED, new Target("New", "amount"), DIRECT))).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.get(project.id()).revision()).isZero();
    }
}
