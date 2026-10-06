package com.example.aimapper.generation.presentation;

import com.example.aimapper.generation.application.*;
import com.example.aimapper.generation.domain.*;
import com.example.aimapper.generation.infrastructure.MappingPlanExcelWriter;
import com.example.aimapper.schema.application.JavaSourceFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.*;

/**
 * 매핑 프로젝트 생성, 사용자 결정, 코드 생성, 엑셀 및 ZIP 다운로드를 제공하는 HTTP API이다.
 */
@RestController
@RequestMapping("/api/v1/mapping-projects")
public class MappingProjectController {
    private final MappingProjectService projects;
    private final MapperGenerationService generator;
    private final ObjectMapper mapper;
    private final MappingPlanExcelWriter excelWriter;
    public MappingProjectController(MappingProjectService projects, MapperGenerationService generator,
                                    ObjectMapper mapper, MappingPlanExcelWriter excelWriter) {
        this.projects = projects;
        this.generator = generator;
        this.mapper = mapper;
        this.excelWriter = excelWriter;
    }

    /** 업로드한 양쪽 Java 소스로 추천과 초기 미매핑 결정을 생성한다. */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public MappingProject create(@RequestPart("asIsFiles") List<MultipartFile> asIs,
                                 @RequestPart("toBeFiles") List<MultipartFile> toBe) {
        return projects.create(sources(asIs), sources(toBe));
    }

    /** 현재 프로젝트 상태와 revision을 조회한다. */
    @GetMapping("/{id}")
    public MappingProject get(@PathVariable UUID id) { return projects.get(id); }

    /** 결정 변경 시 필요한 현재 revision과 변경 내용을 받는다. */
    public record DecisionRequest(long revision, MappingDecision decision) {}

    /** 한 필드의 승인·수정·거절·미매핑 결정을 저장한다. */
    @PutMapping("/{id}/decisions")
    public MappingProject decide(@PathVariable UUID id, @RequestBody DecisionRequest request) {
        return projects.decide(id, request.revision(), request.decision());
    }

    /** 생성에 사용할 revision과 Java 패키지·Mapper 클래스명을 받는다. */
    public record GenerationRequest(long revision, String packageName, String className) {}

    /** 승인된 결정으로 만든 파일과 컴파일 결과를 JSON으로 반환한다. */
    @PostMapping("/{id}/generate")
    public GenerationResult generate(@PathVariable UUID id, @RequestBody GenerationRequest request) {
        return generator.generate(id, request.revision(), request.packageName(), request.className());
    }

    /** 추천과 최종 결정을 비교한 매핑 계획 엑셀을 다운로드한다. */
    @GetMapping(value = "/{id}/mapping-plan.xlsx",
            produces = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
    public ResponseEntity<byte[]> mappingPlan(@PathVariable UUID id, @RequestParam long revision) {
        var project = projects.get(id);
        if (project.revision() != revision) {
            throw new ProjectRevisionConflictException(id, revision, project.revision());
        }
        byte[] excel = excelWriter.write(project);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"mapping-plan-" + id + "-r" + revision + ".xlsx\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .body(excel);
    }

    /** 생성 소스, 테스트, VO, 엑셀과 컴파일 보고서를 하나의 ZIP으로 반환한다. */
    @PostMapping(value = "/{id}/download", produces = "application/zip")
    public ResponseEntity<byte[]> download(@PathVariable UUID id, @RequestBody GenerationRequest request) throws IOException {
        var result = generate(id, request);
        byte[] excel = excelWriter.write(projects.get(id));
        var bytes = new ByteArrayOutputStream();
        try (var zip = new ZipOutputStream(bytes, StandardCharsets.UTF_8)) {
            for (var file : result.files()) {
                zip.putNextEntry(new ZipEntry(file.path()));
                zip.write(file.content().getBytes(StandardCharsets.UTF_8));
                zip.closeEntry();
            }
            zip.putNextEntry(new ZipEntry("compilation-report.json"));
            zip.write(mapper.writeValueAsBytes(Map.of("projectId", id, "revision", result.revision(), "compilation", result.compilation())));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("mapping-plan.xlsx"));
            zip.write(excel);
            zip.closeEntry();
        }
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"mapper-" + id + ".zip\"")
                .header("X-Compilation-Success", Boolean.toString(result.compilation().success()))
                .contentType(MediaType.parseMediaType("application/zip")).body(bytes.toByteArray());
    }

    private List<JavaSourceFile> sources(List<MultipartFile> files) {
        return files.stream().map(file -> {
            try {
                return new JavaSourceFile(file.getOriginalFilename() == null ? "unknown.java" : file.getOriginalFilename(),
                        new String(file.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException ex) { throw new IllegalArgumentException("Failed to read source file", ex); }
        }).toList();
    }
}
