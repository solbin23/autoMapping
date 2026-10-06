package com.example.aimapper.ai.domain;

import java.util.List;

/** 추천 대상, 변환 방식, 신뢰도, 근거와 사용자 검토 필요 여부를 구조화한 AI 응답이다. */
public record AiMappingSuggestion(Target recommendedTarget, ConversionType conversionType,
                                  Double confidence, List<String> reasons, Boolean reviewRequired) {
    public record Target(String className, String path) {}
    public enum ConversionType { DIRECT, NUMERIC_CONVERSION, STRING_PARSE, FORMAT, COLLECTION_MAPPING, CUSTOM, UNKNOWN }

    /** 근거가 부족하거나 호출이 실패했을 때 대상을 추천하지 않는 안전한 결과를 만든다. */
    public static AiMappingSuggestion abstain(String reason) {
        return new AiMappingSuggestion(null, ConversionType.UNKNOWN, 0.0, List.of(reason), true);
    }
}
