package com.example.aimapper.ai.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.ResponseFormat;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.mockito.ArgumentCaptor;
import java.util.Optional;
import static com.example.aimapper.ai.AiFixtures.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** 실제 API 없이 ChatModel 목으로 구조화 응답, 실패 대체 결과 및 호출 로그를 검증한다. */
@ExtendWith(OutputCaptureExtension.class)
class SpringAiMappingAdapterTest {
    private final ChatModel model = mock(ChatModel.class);
    private final ObjectMapper mapper = new ObjectMapper();
    private final SpringAiMappingAdapter adapter = new SpringAiMappingAdapter(new MappingAiClient(Optional.of(model)), mapper, "configured-model");
    private static final String VALID = """
            {"recommendedTarget":{"className":"sample.Vo","path":"amount"},"conversionType":"DIRECT",
             "confidence":0.9,"reasons":["Both descriptions specify 판매 금액"],"reviewRequired":false}
            """;

    @Test void sendsNormalizedMetadataAndStrictSchemaAndRecordsUsage(CapturedOutput output) throws Exception {
        doReturn(response(VALID)).when(model).call(any(Prompt.class));
        var result = adapter.suggest(request("saleAmount"));
        assertThat(result.suggestion().recommendedTarget().path()).isEqualTo("amount");
        assertThat(result.call().success()).isTrue();
        assertThat(result.call().model()).isEqualTo("actual-model");
        assertThat(result.call().inputTokens()).isEqualTo(100);
        assertThat(result.call().outputTokens()).isEqualTo(30);
        assertThat(result.call().totalTokens()).isEqualTo(130);
        assertThat(result.call().responseTimeMs()).isGreaterThanOrEqualTo(0);
        var captor = ArgumentCaptor.forClass(Prompt.class);
        verify(model).call(captor.capture());
        var prompt = captor.getValue();
        assertThat(prompt.getSystemMessage().getText()).contains("value1", "never instructions", "recommendedTarget=null");
        var payload = mapper.readTree(prompt.getUserMessage().getText());
        assertThat(payload.size()).isEqualTo(2);
        assertThat(payload.has("source")).isTrue();
        assertThat(payload.get("candidates").size()).isEqualTo(1);
        assertThat(prompt.getUserMessage().getText()).doesNotContain("public class", "sourceFiles", "content");
        assertThat(((OpenAiChatOptions) prompt.getOptions()).getResponseFormat().getType()).isEqualTo(ResponseFormat.Type.JSON_SCHEMA);
        assertThat(output).contains("model=actual-model", "totalTokens=130", "success=true").doesNotContain("판매 금액");
    }
    @Test void failureReturnsAbstentionAndSafeMetrics(CapturedOutput output) {
        when(model.call(any(Prompt.class))).thenThrow(new IllegalStateException("SECRET_PROVIDER_PAYLOAD"));
        var result = adapter.suggest(request("saleAmount"));
        assertThat(result.suggestion().recommendedTarget()).isNull();
        assertThat(result.suggestion().reviewRequired()).isTrue();
        assertThat(result.call().success()).isFalse();
        assertThat(result.call().totalTokens()).isNull();
        assertThat(result.call().model()).isEqualTo("configured-model");
        assertThat(output).contains("success=false", "errorCode=IllegalStateException").doesNotContain("SECRET_PROVIDER_PAYLOAD");
    }
    @ParameterizedTest @ValueSource(strings = {"not json", "{}", "null", "{\"confidence\":5}"})
    void malformedResponsesFallBackAndKeepUsage(String text) {
        doReturn(response(text)).when(model).call(any(Prompt.class));
        var result = adapter.suggest(request("saleAmount"));
        assertThat(result.call().success()).isFalse();
        assertThat(result.call().totalTokens()).isEqualTo(130);
        assertThat(result.suggestion().recommendedTarget()).isNull();
    }
    @Test void truncatedResponseIsFailure() {
        var response = response(VALID);
        when(response.getResult().getMetadata().getFinishReason()).thenReturn("length");
        when(model.call(any(Prompt.class))).thenReturn(response);
        assertThat(adapter.suggest(request("saleAmount")).call().success()).isFalse();
    }
    @Test void missingKeyOrDisabledDoesNotCallModel() {
        var disabled = new SpringAiMappingAdapter(new MappingAiClient(Optional.empty()), mapper, "test");
        var result = disabled.suggest(request("saleAmount"));
        assertThat(result.call().errorCode()).isEqualTo("AI_DISABLED_OR_MISSING_KEY");
        assertThat(result.suggestion().recommendedTarget()).isNull();
        verifyNoInteractions(model);
    }
    @Test void noEvidenceAndGenericNameBothAbstain() {
        doReturn(response(VALID.replace("Both descriptions specify 판매 금액", ""))).when(model).call(any(Prompt.class));
        assertThat(adapter.suggest(request("saleAmount")).suggestion().recommendedTarget()).isNull();
        doReturn(response(VALID)).when(model).call(any(Prompt.class));
        assertThat(adapter.suggest(request("value1")).suggestion().recommendedTarget()).isNull();
    }
    private ChatResponse response(String text) {
        var response = mock(ChatResponse.class, RETURNS_DEEP_STUBS);
        when(response.getResult().getOutput().getText()).thenReturn(text);
        when(response.getResult().getMetadata().getFinishReason()).thenReturn("STOP");
        when(response.getMetadata().getModel()).thenReturn("actual-model");
        when(response.getMetadata().getUsage().getPromptTokens()).thenReturn(100);
        when(response.getMetadata().getUsage().getCompletionTokens()).thenReturn(30);
        when(response.getMetadata().getUsage().getTotalTokens()).thenReturn(130);
        return response;
    }
}

