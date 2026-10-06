package com.example.aimapper.generation;

import com.example.aimapper.ai.application.AiMappingService;
import com.example.aimapper.ai.infrastructure.*;
import com.example.aimapper.generation.application.MappingProjectService;
import com.example.aimapper.matching.application.FieldMatchingService;
import com.example.aimapper.matching.domain.rule.*;
import com.example.aimapper.schema.application.*;
import com.example.aimapper.schema.infrastructure.JavaParserSchemaAnalyzer;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.*;

/** 프로젝트·결정·생성 서비스 테스트에서 사용할 VO 소스와 승인 계획을 만든다. */
public final class GenerationFixtures {
    private GenerationFixtures() {}
    public static MappingProjectService projects() {
        var matching = new FieldMatchingService(List.of(new FieldNameMatchingRule(), new JavaTypeMatchingRule(),
                new NestedStructureMatchingRule(), new CommentMatchingRule(), new AnnotationMatchingRule()));
        var ai = new AiMappingService(new SpringAiMappingAdapter(new MappingAiClient(Optional.empty()), new ObjectMapper(), "test"), 40);
        return new MappingProjectService(new SchemaAnalysisService(new JavaParserSchemaAnalyzer(), matching, ai));
    }
    public static JavaSourceFile source(String name, String text) { return new JavaSourceFile(name, text); }
}
