package com.example.aimapper.generation.application;

import java.util.UUID;

/** 코드 생성에 사용할 승인 상태의 필드 매핑이 하나도 없을 때 발생한다. */
public class NoApprovedMappingsException extends RuntimeException {
    public NoApprovedMappingsException(UUID projectId) {
        super("At least one approved mapping is required for project " + projectId);
    }
}
