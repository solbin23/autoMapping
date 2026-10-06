package com.example.aimapper.schema.domain;

import java.util.List;

/** 특정 시점에 분석된 한쪽 스키마의 전체 클래스 목록이다. */
public record SchemaSnapshot(SchemaSide side, List<SchemaClass> classes) {
    public SchemaSnapshot {
        classes = List.copyOf(classes);
    }
}
