package com.example.aimapper.schema.domain;

import java.util.List;

/** Java 클래스의 이름과 그 안에 선언된 정규화 필드 목록을 나타낸다. */
public record SchemaClass(
        String packageName,
        String className,
        String qualifiedName,
        List<SchemaField> fields
) {
    public SchemaClass {
        fields = List.copyOf(fields);
    }
}
