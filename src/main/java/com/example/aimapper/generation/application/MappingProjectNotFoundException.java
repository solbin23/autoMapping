package com.example.aimapper.generation.application;

import java.util.UUID;

/** 요청한 ID의 매핑 프로젝트가 저장소에 없을 때 발생한다. */
public class MappingProjectNotFoundException extends RuntimeException {
    public MappingProjectNotFoundException(UUID projectId) {
        super("Unknown mapping project: " + projectId);
    }
}
