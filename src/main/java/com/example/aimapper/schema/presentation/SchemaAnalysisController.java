package com.example.aimapper.schema.presentation;

import com.example.aimapper.schema.application.JavaSourceFile;
import com.example.aimapper.schema.application.SchemaAnalysisService;
import com.example.aimapper.schema.application.SchemaComparisonResult;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.parameters.RequestBody;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

/** AS-IS와 TO-BE Java 파일을 업로드받아 스키마 비교 결과를 반환하는 HTTP API이다. */
@RestController
@RequestMapping("/api/v1/schemas")
public class SchemaAnalysisController {

    private final SchemaAnalysisService analysisService;

    public SchemaAnalysisController(SchemaAnalysisService analysisService) {
        this.analysisService = analysisService;
    }

    @Operation(
            summary = "Analyze Java VO schemas, rank candidates and review ambiguous mappings with AI",
            description = "Upload one or more Java source files for each side. Related custom types must be uploaded together.",
            requestBody = @RequestBody(content = @Content(mediaType = MediaType.MULTIPART_FORM_DATA_VALUE)),
            responses = @ApiResponse(responseCode = "200", description = "Schemas and top 3 candidates per AS-IS field with rule scores and review status")
    )
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SchemaComparisonResult analyze(
            @RequestPart("asIsFiles") List<MultipartFile> asIsFiles,
            @RequestPart("toBeFiles") List<MultipartFile> toBeFiles
    ) {
        return analysisService.analyze(toSources(asIsFiles), toSources(toBeFiles));
    }

    private List<JavaSourceFile> toSources(List<MultipartFile> files) {
        return files.stream().map(file -> {
            try {
                String fileName = file.getOriginalFilename() == null ? "unknown" : file.getOriginalFilename();
                return new JavaSourceFile(fileName, new String(file.getBytes(), StandardCharsets.UTF_8));
            } catch (IOException exception) {
                throw new IllegalArgumentException("Failed to read uploaded file", exception);
            }
        }).toList();
    }
}
