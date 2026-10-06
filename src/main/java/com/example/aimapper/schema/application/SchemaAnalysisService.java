package com.example.aimapper.schema.application;

import com.example.aimapper.schema.domain.SchemaSide;
import com.example.aimapper.schema.domain.SchemaSnapshot;
import com.example.aimapper.matching.application.FieldMatchingService;
import com.example.aimapper.ai.application.AiMappingService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AS-IS와 TO-BE 소스를 분석하고 규칙 기반 후보 및 선택적인 AI 제안을 한 결과로 묶는다.
 */
@Service
public class SchemaAnalysisService {

    private final SchemaAnalysisPort analysisPort;
    private final FieldMatchingService matchingService;
    private final AiMappingService aiMappingService;

    public SchemaAnalysisService(SchemaAnalysisPort analysisPort, FieldMatchingService matchingService, AiMappingService aiMappingService) {
        this.analysisPort = analysisPort;
        this.matchingService = matchingService;
        this.aiMappingService = aiMappingService;
    }

    /** 두 소스 집합을 정규화된 스키마로 만든 뒤 필드 매칭 후보를 계산한다. */
    public SchemaComparisonResult analyze(List<JavaSourceFile> asIsFiles, List<JavaSourceFile> toBeFiles) {
        validateFiles(asIsFiles, "AS-IS");
        validateFiles(toBeFiles, "TO-BE");

        var asIs = new SchemaSnapshot(SchemaSide.AS_IS, analysisPort.analyze(asIsFiles));
        var toBe = new SchemaSnapshot(SchemaSide.TO_BE, analysisPort.analyze(toBeFiles));
        var matches = matchingService.match(asIs, toBe);
        return new SchemaComparisonResult(asIs, toBe, matches, aiMappingService.suggest(matches));
    }

    private void validateFiles(List<JavaSourceFile> files, String side) {
        if (files == null || files.isEmpty()) {
            throw new IllegalArgumentException(side + " Java file is required");
        }
        for (JavaSourceFile file : files) {
            if (!file.fileName().toLowerCase().endsWith(".java")) {
                throw new IllegalArgumentException("Only .java files are allowed: " + file.fileName());
            }
            if (file.content().isBlank()) {
                throw new IllegalArgumentException("Java file must not be empty: " + file.fileName());
            }
        }
    }
}
