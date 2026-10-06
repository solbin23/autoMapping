package com.example.aimapper.generation.domain;

import java.util.List;
import java.util.UUID;

/** 생성된 소스 파일들과 그 파일들을 대상으로 한 컴파일 검증 결과이다. */
public record GenerationResult(UUID projectId, long revision, List<GeneratedFile> files, CompilationResult compilation) {
    public GenerationResult { files = List.copyOf(files); }
}
