package com.example.aimapper.ai.application;

import com.example.aimapper.ai.domain.*;
import com.example.aimapper.matching.domain.*;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.util.List;
import static com.example.aimapper.ai.AiFixtures.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/** 애매하고 최소 점수를 넘은 필드만 AI 포트로 전달되는지 검증한다. */
class AiMappingServiceTest {
    @Test void sendsOnlyAmbiguousFieldsAndCapsCandidatesAtThree() {
        AiMappingPort port = mock(AiMappingPort.class);
        var response = new AiFieldResult(new AiMappingSuggestion.Target("sample.Vo", "saleAmount"),
                AiMappingSuggestion.abstain("Not enough evidence"), new AiCallRecord("test", 1, 1, 2, 1, true, null));
        when(port.suggest(any())).thenReturn(response);
        var candidates = List.of(new MatchCandidate(field("amount"), 70, List.of()),
                new MatchCandidate(field("salePrice"), 69, List.of()), new MatchCandidate(field("total"), 68, List.of()),
                new MatchCandidate(field("other"), 67, List.of()));
        var ambiguous = new FieldMatch(field("saleAmount"), FieldMatch.Status.REVIEW_REQUIRED, "tie", candidates);
        var result = new AiMappingService(port, 40).suggest(List.of(
                match("certain", FieldMatch.Status.AUTO_MATCHED, 100),
                match("weak", FieldMatch.Status.REVIEW_REQUIRED, 39.99),
                new FieldMatch(field("empty"), FieldMatch.Status.NO_CANDIDATE, "empty", List.of()), ambiguous));
        assertThat(result).containsExactly(response);
        var captor = ArgumentCaptor.forClass(AiMappingRequest.class);
        verify(port).suggest(captor.capture());
        assertThat(captor.getValue().source().field().path()).isEqualTo("saleAmount");
        assertThat(captor.getValue().candidates()).hasSize(3);
        verifyNoMoreInteractions(port);
    }
    @Test void includesMinimumScoreBoundary() {
        var port = mock(AiMappingPort.class);
        new AiMappingService(port, 40).suggest(List.of(match("saleAmount", FieldMatch.Status.REVIEW_REQUIRED, 40)));
        verify(port).suggest(any());
    }
}
