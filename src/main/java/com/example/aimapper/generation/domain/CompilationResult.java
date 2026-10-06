package com.example.aimapper.generation.domain;

import java.util.List;

/** 생성 코드의 컴파일 성공 여부와 파일·행·열 단위 진단 목록이다. */
public record CompilationResult(boolean success, List<Problem> errors) {
    public CompilationResult { errors = List.copyOf(errors); }
    /** javac가 보고한 개별 오류 위치, 코드와 메시지이다. */
    public record Problem(String file, long line, long column, String code, String message) {}
}
