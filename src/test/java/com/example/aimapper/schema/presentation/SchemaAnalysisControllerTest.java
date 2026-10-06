package com.example.aimapper.schema.presentation;

import com.example.aimapper.schema.application.SchemaAnalysisService;
import com.example.aimapper.matching.application.FieldMatchingService;
import com.example.aimapper.matching.domain.rule.*;
import java.util.List;
import com.example.aimapper.ai.application.AiMappingService;
import com.example.aimapper.ai.infrastructure.MappingAiClient;
import com.example.aimapper.ai.infrastructure.SpringAiMappingAdapter;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import com.example.aimapper.schema.infrastructure.JavaParserSchemaAnalyzer;
import com.example.aimapper.common.presentation.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 멀티파트 스키마 분석 API의 정상 응답과 잘못된 요청 처리를 검증한다. */
class SchemaAnalysisControllerTest {

    private final MockMvc mockMvc = MockMvcBuilders
            .standaloneSetup(new SchemaAnalysisController(new SchemaAnalysisService(new JavaParserSchemaAnalyzer(),
                    new FieldMatchingService(List.of(new FieldNameMatchingRule(), new JavaTypeMatchingRule(),
                            new NestedStructureMatchingRule(), new CommentMatchingRule(), new AnnotationMatchingRule())),
                    new AiMappingService(new SpringAiMappingAdapter(new MappingAiClient(Optional.empty()), new ObjectMapper(), "test"), 40))))
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();

    @Test
    void analyzesUploadedAsIsAndToBeFiles() throws Exception {
        var asIs = javaFile("asIsFiles", "LegacyProductVo.java", """
                package sample.legacy;
                public class LegacyProductVo { private String value1; private String saleAmount; }
                """);
        var toBe = javaFile("toBeFiles", "ProductRequest.java", """
                package sample.commerce;
                import java.math.BigDecimal;
                public class ProductRequest { private BigDecimal amount; private Boolean sellable; }
                """);

        mockMvc.perform(multipart("/api/v1/schemas/analyze").file(asIs).file(toBe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asIs.side").value("AS_IS"))
                .andExpect(jsonPath("$.asIs.classes[0].fields[0].path").value("value1"))
                .andExpect(jsonPath("$.toBe.side").value("TO_BE"))
                .andExpect(jsonPath("$.toBe.classes[0].fields[0].javaType").value("java.math.BigDecimal"))
                .andExpect(jsonPath("$.matches.length()").value(2))
                .andExpect(jsonPath("$.matches[0].source.className").value("sample.legacy.LegacyProductVo"))
                .andExpect(jsonPath("$.matches[0].status").value("REVIEW_REQUIRED"))
                .andExpect(jsonPath("$.matches[0].candidates.length()").value(2))
                .andExpect(jsonPath("$.matches[0].candidates[0].score").isNumber())
                .andExpect(jsonPath("$.matches[0].candidates[0].evidence.length()").value(5))
                .andExpect(jsonPath("$.matches[0].candidates[0].evidence[0].result.reason").isString());
    }

    @Test
    void matchesParsedNestedFieldsAndMetadata() throws Exception {
        var asIs = javaFile("asIsFiles", "Legacy.java", """
                import java.util.List;
                class Legacy { private List<OldItem> items; }
                class OldItem { /** 판매 금액 */ @Deprecated private int amount; }
                """);
        var toBe = javaFile("toBeFiles", "Modern.java", """
                import java.util.List;
                class Modern { private List<NewItem> items; }
                class NewItem { /** 판매 금액 */ @Deprecated private long amount; }
                """);
        mockMvc.perform(multipart("/api/v1/schemas/analyze").file(asIs).file(toBe))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.matches[0].source.field.path").value("items[].amount"))
                .andExpect(jsonPath("$.matches[0].candidates[0].target.field.path").value("items[].amount"))
                .andExpect(jsonPath("$.matches[0].candidates[0].score").value(95.0))
                .andExpect(jsonPath("$.matches[0].status").value("AUTO_MATCHED"));
    }

    @Test
    void rejectsNonJavaFiles() throws Exception {
        var asIs = javaFile("asIsFiles", "LegacyProductVo.txt", "not java");
        var toBe = javaFile("toBeFiles", "ProductRequest.java", "public class ProductRequest {}");

        mockMvc.perform(multipart("/api/v1/schemas/analyze").file(asIs).file(toBe))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only .java files are allowed: LegacyProductVo.txt"));
    }

    private MockMultipartFile javaFile(String partName, String fileName, String content) {
        return new MockMultipartFile(partName, fileName, MediaType.TEXT_PLAIN_VALUE, content.getBytes());
    }
}
