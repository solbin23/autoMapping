package com.example.aimapper.execution.presentation;

import com.example.aimapper.execution.application.MappingExecutionService;
import com.example.aimapper.execution.domain.MappingExecutionResult;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/** 외부 시스템이 승인된 매핑 계획으로 실제 JSON 데이터를 변환하는 HTTP API이다. */
@RestController
@RequestMapping("/api/v1/mapping-projects")
public class MappingExecutionController {
    private final MappingExecutionService executionService;

    public MappingExecutionController(MappingExecutionService executionService) {
        this.executionService = executionService;
    }

    /** 실행할 revision, 루트 클래스 쌍과 AS-IS JSON을 받는다. */
    public record ExecutionRequest(long revision, String sourceClass, String targetClass, JsonNode sourceData) {
    }

    /** 승인 상태의 필드만 변환해 TO-BE JSON과 적용 매핑 수를 반환한다. */
    @PostMapping("/{id}/execute")
    public MappingExecutionResult execute(@PathVariable UUID id, @RequestBody ExecutionRequest request) {
        return executionService.execute(id, request.revision(), request.sourceClass(), request.targetClass(),
                request.sourceData());
    }
}
