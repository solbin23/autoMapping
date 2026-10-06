package com.example.aimapper.generation.domain;

import com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType;
import com.example.aimapper.ai.domain.AiMappingSuggestion.Target;

/** 추천과 별도로 저장되는 사용자의 최종 필드 매핑 결정이다. */
public record MappingDecision(Target source, Status status, Target target, ConversionType conversionType) {
    /** 승인, 수정 중, 거절, 미결정 상태를 구분하며 승인 상태만 코드 생성에 사용된다. */
    public enum Status { APPROVED, MODIFIED, REJECTED, UNMAPPED }
}
