package com.example.aimapper.ai.application;

import com.example.aimapper.ai.infrastructure.*;
import com.example.aimapper.matching.application.FieldMatchingService;
import com.example.aimapper.matching.domain.rule.*;
import com.example.aimapper.schema.application.*;
import com.example.aimapper.schema.infrastructure.JavaParserSchemaAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 개별 AI 호출 실패가 규칙 후보를 없애거나 다음 필드 처리를 막지 않는지 검증한다. */
class AiFailureFallbackTest {
    @Test void failedFieldDoesNotLoseRulesOrPreventNextAiSuggestion() {
        var model = mock(ChatModel.class);
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("timeout"));
        var rules = new FieldMatchingService(List.of(new FieldNameMatchingRule(), new JavaTypeMatchingRule(),
                new NestedStructureMatchingRule(), new CommentMatchingRule(), new AnnotationMatchingRule()));
        var adapter = new SpringAiMappingAdapter(new MappingAiClient(Optional.of(model)), new ObjectMapper(), "test");
        var service = new SchemaAnalysisService(new JavaParserSchemaAnalyzer(), rules, new AiMappingService(adapter, 40));
        var result = service.analyze(List.of(new JavaSourceFile("Old.java", "class Old { String saleAmount; String salePrice; }")),
                List.of(new JavaSourceFile("New.java", "class New { String amount; String price; }")));
        assertThat(result.matches()).hasSize(2).allSatisfy(match -> assertThat(match.candidates()).hasSize(2));
        assertThat(result.matches()).isEqualTo(rules.match(result.asIs(), result.toBe()));
        assertThat(result.aiSuggestions()).hasSize(2).allSatisfy(resultItem -> {
            assertThat(resultItem.call().success()).isFalse();
            assertThat(resultItem.suggestion().recommendedTarget()).isNull();
        });
        verify(model, times(2)).call(any(Prompt.class));
    }
}
