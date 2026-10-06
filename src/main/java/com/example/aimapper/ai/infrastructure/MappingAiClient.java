package com.example.aimapper.ai.infrastructure;

import org.springframework.ai.chat.model.ChatModel;
import java.util.Optional;

/** API 키 또는 기능 설정에 따라 존재할 수도 있는 Spring AI 모델을 감싼다. */
public record MappingAiClient(Optional<ChatModel> model) {}
