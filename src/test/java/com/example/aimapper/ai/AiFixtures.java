package com.example.aimapper.ai;

import com.example.aimapper.ai.application.AiMappingRequest;
import com.example.aimapper.matching.domain.*;
import com.example.aimapper.schema.domain.SchemaField;
import java.util.List;

/** AI 요청 및 응답 검증 테스트에서 사용할 정규화 필드와 후보를 생성한다. */
public final class AiFixtures {
    private AiFixtures() {}
    public static FieldReference field(String name) {
        return new FieldReference("sample.Vo", new SchemaField(name, name, "java.lang.String", false, true, "판매 금액", List.of()));
    }
    public static AiMappingRequest request(String name) {
        return AiMappingRequest.from(match(name, FieldMatch.Status.REVIEW_REQUIRED, 70));
    }
    public static FieldMatch match(String name, FieldMatch.Status status, double score) {
        return new FieldMatch(field(name), status, "test", List.of(new MatchCandidate(field("amount"), score, List.of())));
    }
}
