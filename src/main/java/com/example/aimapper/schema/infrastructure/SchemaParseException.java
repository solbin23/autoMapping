package com.example.aimapper.schema.infrastructure;

/** Java 소스를 구문 분석할 수 없을 때 파일명과 상세 원인을 전달하는 예외이다. */
public class SchemaParseException extends RuntimeException {

    public SchemaParseException(String fileName, String detail) {
        super("Failed to parse Java source '" + fileName + "': " + detail);
    }
}
