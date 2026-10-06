package com.example.aimapper.schema.domain;

import java.util.List;

/** 매칭에 필요한 필드 경로, 타입, 컬렉션 여부, 설명 및 어노테이션을 보관한다. */
public record SchemaField(
        String path,
        String fieldName,
        String javaType,
        boolean collection,
        boolean nullable,
        String description,
        List<String> annotations
) {
    public SchemaField {
        annotations = List.copyOf(annotations);
    }
}
