package com.example.aimapper.ai.application;

import com.example.aimapper.ai.domain.*;
import com.example.aimapper.matching.domain.FieldMatch;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.util.List;

/** 규칙 점수가 애매하고 최소 점수를 넘은 필드만 AI 검토 대상으로 선별한다. */
@Service
public class AiMappingService {
    private final AiMappingPort port;
    private final double minimumScore;

    public AiMappingService(AiMappingPort port, @Value("${mapping.ai.minimum-score:40}") double minimumScore) {
        if (!Double.isFinite(minimumScore) || minimumScore < 0 || minimumScore > 100) {
            throw new IllegalArgumentException("AI minimum score must be between 0 and 100");
        }
        this.port = port;
        this.minimumScore = minimumScore;
    }

    /** 검토 필요 상태 중 AI 호출 가치가 있는 필드만 포트에 전달한다. */
    public List<AiFieldResult> suggest(List<FieldMatch> matches) {
        return matches.stream().filter(m -> m.status() == FieldMatch.Status.REVIEW_REQUIRED)
                .filter(m -> !m.candidates().isEmpty() && m.candidates().getFirst().score() >= minimumScore)
                .map(AiMappingRequest::from).map(port::suggest).toList();
    }
}
