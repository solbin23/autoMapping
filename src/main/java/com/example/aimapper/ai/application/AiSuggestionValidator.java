package com.example.aimapper.ai.application;

import com.example.aimapper.ai.domain.AiMappingSuggestion;
import com.example.aimapper.matching.domain.rule.FieldNameMatchingRule;
import java.util.List;

/**
 * 모델 응답이 허용된 후보와 형식을 지키는지 검사하고 보수적인 검토 필요 여부를 적용한다.
 */
public class AiSuggestionValidator {
    /** 근거 없는 응답, 후보 밖 대상, 의미 없는 필드명을 거부하고 안전한 제안만 반환한다. */
    public AiMappingSuggestion validate(AiMappingRequest request, AiMappingSuggestion answer) {
        if (answer == null || answer.confidence() == null || !Double.isFinite(answer.confidence())
                || answer.confidence() < 0 || answer.confidence() > 1 || answer.conversionType() == null
                || answer.reviewRequired() == null) throw new IllegalArgumentException("Invalid AI response");
        List<String> reasons = answer.reasons() == null ? List.of() : answer.reasons().stream()
                .filter(r -> r != null && !r.isBlank()).toList();
        if (reasons.isEmpty()) return AiMappingSuggestion.abstain("No supporting evidence returned");
        if (answer.recommendedTarget() == null) return AiMappingSuggestion.abstain(String.join("; ", reasons));
        var selected = request.candidates().stream().filter(c ->
                c.target().className().equals(answer.recommendedTarget().className())
                        && c.target().field().path().equals(answer.recommendedTarget().path())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Target is outside supplied candidates"));
        // 프롬프트를 따르지 않는 모델 응답도 차단하도록 서버에서 의미 없는 이름을 다시 검사한다.
        if (FieldNameMatchingRule.isMeaningless(request.source().field().fieldName())
                || FieldNameMatchingRule.isMeaningless(selected.target().field().fieldName())) {
            return AiMappingSuggestion.abstain("Generic field names do not establish business meaning");
        }
        boolean review = answer.reviewRequired() || answer.confidence() < 0.85
                || answer.conversionType() != AiMappingSuggestion.ConversionType.DIRECT
                || selected.evidence().stream().anyMatch(e -> e.result().blocksAutoMatch());
        return new AiMappingSuggestion(answer.recommendedTarget(), answer.conversionType(), answer.confidence(), reasons, review);
    }
}
