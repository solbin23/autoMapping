package com.example.aimapper.execution.infrastructure;

import com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType;
import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;
import com.example.aimapper.execution.application.MappingExecutionException;
import com.example.aimapper.generation.domain.MappingDecision;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.example.aimapper.generation.GenerationFixtures.projects;
import static com.example.aimapper.generation.GenerationFixtures.source;
import static org.assertj.core.api.Assertions.*;

/** 승인된 결정만 사용한 JSON 기본 변환, 중첩 컬렉션 및 실패 진단을 검증한다. */
class JsonMappingEngineTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final JsonMappingEngine engine = new JsonMappingEngine(mapper);

    @Test
    void mapsOnlyApprovedScalarFieldsWithConfiguredConversions() throws Exception {
        var projects = projects();
        var project = projects.create(
                List.of(source("OldOrder.java", """
                        public class OldOrder {
                            private String amount;
                            private boolean enabled;
                            private String internalMemo;
                        }
                        """)),
                List.of(source("NewOrder.java", """
                        public class NewOrder {
                            private int total;
                            private boolean active;
                            private String memo;
                        }
                        """)));

        project = projects.decide(project.id(), 0, approved("OldOrder", "amount", "NewOrder", "total",
                ConversionType.STRING_PARSE));
        project = projects.decide(project.id(), 1, approved("OldOrder", "enabled", "NewOrder", "active",
                ConversionType.DIRECT));
        project = projects.decide(project.id(), 2, new MappingDecision(new Target("OldOrder", "internalMemo"),
                MappingDecision.Status.REJECTED, null, null));

        var result = engine.execute(project, "OldOrder", "NewOrder",
                mapper.readTree("{\"amount\":\"12500\",\"enabled\":true,\"internalMemo\":\"secret\"}"));

        assertThat(result.appliedMappings()).isEqualTo(2);
        assertThat(result.targetData().path("total").intValue()).isEqualTo(12500);
        assertThat(result.targetData().path("active").booleanValue()).isTrue();
        assertThat(result.targetData().has("memo")).isFalse();
    }

    @Test
    void preservesIndexesAndSiblingValuesInNestedCollections() throws Exception {
        var projects = projects();
        var project = projects.create(
                List.of(source("Old.java", """
                        import java.util.List;
                        class Old { private List<OldItem> items; }
                        class OldItem { private String code; private int quantity; }
                        """)),
                List.of(source("New.java", """
                        import java.util.List;
                        class New { private List<NewLine> lines; }
                        class NewLine { private String sku; private int count; }
                        """)));

        project = projects.decide(project.id(), 0, approved("Old", "items[].code", "New", "lines[].sku",
                ConversionType.DIRECT));
        project = projects.decide(project.id(), 1, approved("Old", "items[].quantity", "New", "lines[].count",
                ConversionType.DIRECT));

        var result = engine.execute(project, "Old", "New", mapper.readTree("""
                {"items":[{"code":"SKU-1","quantity":2},{"code":"SKU-2","quantity":5}]}
                """));

        assertThat(result.targetData().at("/lines/0/sku").textValue()).isEqualTo("SKU-1");
        assertThat(result.targetData().at("/lines/0/count").intValue()).isEqualTo(2);
        assertThat(result.targetData().at("/lines/1/sku").textValue()).isEqualTo("SKU-2");
        assertThat(result.targetData().at("/lines/1/count").intValue()).isEqualTo(5);

        var empty = engine.execute(project, "Old", "New", mapper.readTree("{\"items\":[]}"));
        assertThat(empty.targetData().path("lines").isArray()).isTrue();
        assertThat(empty.targetData().path("lines").isEmpty()).isTrue();
    }

    @Test
    void reportsSourceAndTargetPathsWhenRuntimeValueCannotBeConverted() throws Exception {
        var projects = projects();
        var project = projects.create(
                List.of(source("Old.java", "class Old { String amount; }")),
                List.of(source("New.java", "class New { int total; }")));
        project = projects.decide(project.id(), 0, approved("Old", "amount", "New", "total",
                ConversionType.STRING_PARSE));

        var approvedProject = project;
        assertThatThrownBy(() -> engine.execute(approvedProject, "Old", "New",
                mapper.readTree("{\"amount\":\"not-a-number\"}")))
                .isInstanceOf(MappingExecutionException.class)
                .hasMessageContaining("Old:amount")
                .hasMessageContaining("New:total")
                .hasMessageContaining("numeric");
    }

    private MappingDecision approved(String sourceClass, String sourcePath, String targetClass,
                                     String targetPath, ConversionType conversionType) {
        return new MappingDecision(new Target(sourceClass, sourcePath), MappingDecision.Status.APPROVED,
                new Target(targetClass, targetPath), conversionType);
    }
}
