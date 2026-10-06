package com.example.aimapper.ai.application;

import com.example.aimapper.ai.domain.AiMappingSuggestion;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;
import static com.example.aimapper.ai.AiFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static com.example.aimapper.ai.domain.AiMappingSuggestion.ConversionType.*;

/** 근거 누락, 후보 밖 추천, 무의미한 이름 및 신뢰도에 대한 AI 응답 검증을 확인한다. */
class AiSuggestionValidatorTest {
    private final AiSuggestionValidator validator = new AiSuggestionValidator();
    @Test void clearsRecommendationWithoutEvidence() {
        var answer = new AiMappingSuggestion(new AiMappingSuggestion.Target("sample.Vo", "amount"), DIRECT, .9, List.of(" "), false);
        var result = validator.validate(request("saleAmount"), answer);
        assertThat(result.recommendedTarget()).isNull();
        assertThat(result.reviewRequired()).isTrue();
    }
    @Test void rejectsTargetsOutsideCandidates() {
        var answer = new AiMappingSuggestion(new AiMappingSuggestion.Target("other.Vo", "amount"), DIRECT, .9, List.of("evidence"), false);
        assertThatThrownBy(() -> validator.validate(request("saleAmount"), answer)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void genericNamesCannotBeGuessedEvenWithClaimedEvidence() {
        var answer = new AiMappingSuggestion(new AiMappingSuggestion.Target("sample.Vo", "amount"), DIRECT, 1.0, List.of("It probably means amount"), false);
        assertThat(validator.validate(request("value1"), answer).recommendedTarget()).isNull();
    }
    @ParameterizedTest @ValueSource(doubles = {-0.1, 1.1, Double.NaN, Double.POSITIVE_INFINITY})
    void rejectsInvalidConfidence(double confidence) {
        var answer = new AiMappingSuggestion(null, UNKNOWN, confidence, List.of("insufficient"), true);
        assertThatThrownBy(() -> validator.validate(request("saleAmount"), answer)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void conversionAndLowConfidenceForceReview() {
        var answer = new AiMappingSuggestion(new AiMappingSuggestion.Target("sample.Vo", "amount"), STRING_PARSE, .99, List.of("format differs"), false);
        assertThat(validator.validate(request("saleAmount"), answer).reviewRequired()).isTrue();
        answer = new AiMappingSuggestion(answer.recommendedTarget(), DIRECT, .5, answer.reasons(), false);
        assertThat(validator.validate(request("saleAmount"), answer).reviewRequired()).isTrue();
    }
}
