package com.example.aimapper.ai.application;

import com.example.aimapper.ai.domain.AiFieldResult;

/** AI 공급자와 애플리케이션 로직 사이를 분리하는 매핑 제안 호출 포트이다. */
public interface AiMappingPort {
    /** 정규화된 한 필드와 상위 후보만 전달해 보조 제안을 요청한다. */
    AiFieldResult suggest(AiMappingRequest request);
}
