package com.example.aimapper.ai.infrastructure;

import org.junit.jupiter.api.Test;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** API 키 누락 또는 명시적 비활성화 시 ChatModel을 만들지 않는지 검증한다. */
class AiMappingConfigurationTest {
    @Test void supportsNoKeyAndExplicitDisable() {
        var configuration = new AiMappingConfiguration();
        assertThat(configuration.mappingChatModel("", true, "test", Duration.ofSeconds(1)).model()).isEmpty();
        assertThat(configuration.mappingChatModel("dummy-key", false, "test", Duration.ofSeconds(1)).model()).isEmpty();
    }
    @Test void constructsSpringAiModelWithoutNetworkCall() {
        assertThat(new AiMappingConfiguration().mappingChatModel("dummy-key", true, "gpt-4o-mini", Duration.ofSeconds(1)).model()).isPresent();
    }
}
