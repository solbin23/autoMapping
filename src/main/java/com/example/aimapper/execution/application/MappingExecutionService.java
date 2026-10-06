package com.example.aimapper.execution.application;

import com.example.aimapper.execution.domain.MappingExecutionResult;
import com.example.aimapper.execution.infrastructure.JsonMappingEngine;
import com.example.aimapper.generation.application.MappingProjectService;
import com.example.aimapper.generation.application.ProjectRevisionConflictException;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Service;

import java.util.UUID;

/** 저장된 승인 계획을 조회해 외부 시스템의 실제 JSON 변환 요청을 실행한다. */
@Service
public class MappingExecutionService {
    private final MappingProjectService projects;
    private final JsonMappingEngine engine;

    public MappingExecutionService(MappingProjectService projects, JsonMappingEngine engine) {
        this.projects = projects;
        this.engine = engine;
    }

    /** 요청 revision을 확인한 뒤 AI나 코드 생성 없이 승인된 매핑만 실행한다. */
    public MappingExecutionResult execute(UUID projectId, long expectedRevision, String sourceClass,
                                          String targetClass, JsonNode sourceData) {
        var project = projects.get(projectId);
        if (project.revision() != expectedRevision) {
            throw new ProjectRevisionConflictException(projectId, expectedRevision, project.revision());
        }
        return engine.execute(project, sourceClass, targetClass, sourceData);
    }
}
