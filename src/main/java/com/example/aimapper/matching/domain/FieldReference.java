package com.example.aimapper.matching.domain;

import com.example.aimapper.schema.domain.SchemaField;

/** 후보 비교 과정에서 필드가 속한 클래스 이름과 필드 정보를 함께 참조한다. */
public record FieldReference(String className, SchemaField field) {}
