package com.example.aimapper;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/** AI 키 없이도 전체 Spring 애플리케이션 컨텍스트가 시작되는지 검증한다. */
@SpringBootTest(properties = {"mapping.ai.enabled=false", "spring.ai.openai.api-key="})
class AiVoMappingAssistantApplicationTests {

    @Test
    void contextLoads() {
    }
}
