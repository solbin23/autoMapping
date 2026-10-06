package com.example.aimapper.schema.application;

import java.util.Objects;

/**
 * 사용자가 업로드한 Java 소스의 파일명과 원문을 전달하는 입력 모델이다.
 */
public record JavaSourceFile(String fileName, String content) {
    public JavaSourceFile {
        Objects.requireNonNull(fileName, "fileName must not be null");
        Objects.requireNonNull(content, "content must not be null");
    }
}
