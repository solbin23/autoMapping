package com.example.aimapper.generation.application;

import java.util.UUID;

/** 클라이언트의 revision이 현재 프로젝트 revision과 다를 때 발생한다. */
public class ProjectRevisionConflictException extends RuntimeException {
    public ProjectRevisionConflictException(UUID projectId, long expectedRevision, long actualRevision) {
        super("Project revision conflict for " + projectId
                + ": expected " + expectedRevision + " but current revision is " + actualRevision);
    }
}
