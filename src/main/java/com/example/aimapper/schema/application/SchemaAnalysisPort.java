package com.example.aimapper.schema.application;

import com.example.aimapper.schema.domain.SchemaClass;

import java.util.List;

/**
 * Java 소스를 스키마 모델로 변환하는 분석기의 애플리케이션 포트이다.
 * 구현체를 교체해도 상위 분석 흐름이 영향을 받지 않게 한다.
 */
public interface SchemaAnalysisPort {
    /** 업로드된 소스들을 분석해 선언된 클래스와 필드 정보를 반환한다. */
    List<SchemaClass> analyze(List<JavaSourceFile> sourceFiles);
}
