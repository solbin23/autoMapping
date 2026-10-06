package com.example.aimapper.generation.presentation;

import com.example.aimapper.generation.application.*;
import com.example.aimapper.generation.infrastructure.JavaCompilationVerifier;
import com.example.aimapper.generation.infrastructure.MappingPlanExcelWriter;
import com.example.aimapper.common.presentation.GlobalExceptionHandler;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipInputStream;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import static com.example.aimapper.generation.GenerationFixtures.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static org.assertj.core.api.Assertions.assertThat;

/** 프로젝트 API부터 승인, 생성, 엑셀 및 ZIP 다운로드까지의 HTTP 흐름을 검증한다. */
class MappingProjectControllerTest {
    private final MappingProjectService projects = projects();
    private final ObjectMapper mapper = new ObjectMapper();
    private final MockMvc mvc = MockMvcBuilders.standaloneSetup(new MappingProjectController(projects,
            new MapperGenerationService(projects, new JavaCompilationVerifier()), mapper,
            new MappingPlanExcelWriter())).setControllerAdvice(new GlobalExceptionHandler()).build();

    @Test void uploadApproveGenerateAndDownloadFiles() throws Exception {
        String id = create("public class Old { private String amount; }", "public class New { private int amount; }");
        approve(id);
        mvc.perform(get("/api/v1/mapping-projects/" + id)).andExpect(status().isOk())
                .andExpect(jsonPath("$.decisions[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.recommendations.matches[0].status").value("REVIEW_REQUIRED"));
        String body = "{\"revision\":1,\"packageName\":\"\",\"className\":\"ApprovedMapper\"}";
        mvc.perform(post("/api/v1/mapping-projects/" + id + "/generate").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(jsonPath("$.compilation.success").value(true))
                .andExpect(jsonPath("$.files[0].path").value("src/main/java/ApprovedMapper.java"));
        var download = mvc.perform(post("/api/v1/mapping-projects/" + id + "/download").contentType("application/json").content(body))
                .andExpect(status().isOk()).andExpect(header().string("X-Compilation-Success", "true"))
                .andExpect(content().contentType("application/zip")).andReturn().getResponse().getContentAsByteArray();
        Set<String> entries = new HashSet<>();
        byte[] excel = null;
        try (var zip = new ZipInputStream(new ByteArrayInputStream(download))) {
            for (var entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
                entries.add(entry.getName());
                if (entry.getName().equals("mapping-plan.xlsx")) excel = zip.readAllBytes();
            }
        }
        assertThat(entries).contains("src/main/java/ApprovedMapper.java", "src/test/java/ApprovedMapperTest.java",
                "compilation-report.json", "mapping-plan.xlsx");
        assertMappingPlan(excel, "APPROVED", "amount", "Y");

        var directExcel = mvc.perform(get("/api/v1/mapping-projects/" + id + "/mapping-plan.xlsx")
                        .param("revision", "1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString(".xlsx")))
                .andReturn().getResponse().getContentAsByteArray();
        assertMappingPlan(directExcel, "APPROVED", "amount", "Y");
    }
    @Test void returnsCompilerErrorLocationsWithoutFailingHttpRequest() throws Exception {
        String id = create("public class Old { String amount; MissingType broken; }", "public class New { int amount; }");
        approve(id);
        mvc.perform(post("/api/v1/mapping-projects/" + id + "/generate").contentType("application/json")
                        .content("{\"revision\":1,\"packageName\":\"\",\"className\":\"Mapper\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.compilation.success").value(false))
                .andExpect(jsonPath("$.compilation.errors[0].file").value("sources/as-is/Old.java"))
                .andExpect(jsonPath("$.compilation.errors[0].line").value(1))
                .andExpect(jsonPath("$.compilation.errors[0].column").isNumber())
                .andExpect(jsonPath("$.compilation.errors[0].message").isNotEmpty());
    }
    private String create(String old, String target) throws Exception {
        var response = mvc.perform(multipart("/api/v1/mapping-projects")
                .file(new MockMultipartFile("asIsFiles", "Old.java", "text/plain", old.getBytes(java.nio.charset.StandardCharsets.UTF_8)))
                .file(new MockMultipartFile("toBeFiles", "New.java", "text/plain", target.getBytes(java.nio.charset.StandardCharsets.UTF_8))))
                .andExpect(status().isOk()).andExpect(jsonPath("$.decisions[0].status").value("UNMAPPED")).andReturn().getResponse().getContentAsString();
        return mapper.readTree(response).get("id").asText();
    }
    private void approve(String id) throws Exception {
        mvc.perform(put("/api/v1/mapping-projects/" + id + "/decisions").contentType("application/json").content("""
                {"revision":0,"decision":{"source":{"className":"Old","path":"amount"},"status":"APPROVED",
                 "target":{"className":"New","path":"amount"},"conversionType":"STRING_PARSE"}}
                """)).andExpect(status().isOk()).andExpect(jsonPath("$.revision").value(1));
    }

    private void assertMappingPlan(byte[] excel, String status, String targetPath, String included) throws Exception {
        assertThat(excel).isNotNull().isNotEmpty();
        try (var workbook = WorkbookFactory.create(new ByteArrayInputStream(excel))) {
            var sheet = workbook.getSheet("매핑 계획");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("AS-IS 클래스");
            assertThat(sheet.getRow(1).getCell(6).getStringCellValue()).isEqualTo(status);
            assertThat(sheet.getRow(1).getCell(8).getStringCellValue()).isEqualTo(targetPath);
            assertThat(sheet.getRow(1).getCell(11).getStringCellValue()).isEqualTo(included);
            assertThat(workbook.getSheet("내보내기 정보")).isNotNull();
        }
    }
}
