package com.example.aimapper.matching.domain;

/** 종합 점수를 설명하기 위한 규칙 ID, 가중치 및 개별 평가 결과이다. */
public record RuleEvidence(String rule, double weight, RuleScore result) {}
