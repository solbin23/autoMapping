# MVP 2: 규칙 기반 필드 매칭

분석부터 승인과 코드 생성까지의 호출 순서는 [매핑 시스템 시퀀스 다이어그램](docs/architecture/mapping-sequence.md)에서 확인할 수 있습니다.

`POST /api/v1/schemas/analyze`에 기존처럼 `asIsFiles`, `toBeFiles`를 multipart로 전달합니다.
응답은 기존 `asIs`, `toBe`, `matches`와 AI 검토 결과 `aiSuggestions`를 포함합니다.

각 AS-IS 클래스의 모든 평탄화 필드를 모든 TO-BE 클래스의 필드와 비교합니다.
필드 식별자는 `className`(정규 클래스명)과 `field.path`의 조합입니다.
후보는 점수 내림차순으로 최대 3개이며, 동점은 대상 클래스명과 경로 순입니다.
대상 필드가 없으면 빈 후보와 `NO_CANDIDATE`를 반환합니다.

| 규칙 ID | 가중치 | 평가 |
| --- | ---: | --- |
| field-name | 40 | 대소문자·구분자 정규화, 편집 거리 및 단어 집합 유사도 중 큰 값 |
| java-type | 25 | 동일 타입 100, boxing/unboxing 95, 숫자 widening 80; 위험 변환은 검토 |
| nested-structure | 15 | 경로 깊이 40%, 부모명 40%, 컬렉션 위치를 포함한 형태 20% |
| comment | 15 | 주석 텍스트 유사도; 한쪽이라도 없으면 계산 제외 |
| annotation | 5 | 이름·속성값 집합 Jaccard 유사도; 양쪽 모두 없으면 계산 제외 |

후보 `score`는 적용 가능한 규칙의 가중 평균(0~100, 소수 둘째 자리)입니다.
`evidence`에 각 규칙의 가중치, 점수, 적용 여부, 자동 확정 차단 여부, 근거를 제공합니다.
어노테이션은 단순명으로 비교하며 속성 순서와 value 축약 표기를 정규화합니다.
다른 패키지의 같은 어노테이션 이름은 같다고 평가될 수 있습니다.

`AUTO_MATCHED`는 최고 후보가 85점 이상이고 2위와 10점 이상 차이 나며 차단 규칙이 없을 때입니다.
후보가 하나면 점수 차 조건을 생략합니다. 나머지는 `REVIEW_REQUIRED`입니다.
`value1`, `field2`, `option1`, `tmp` 같은 일반명은 어느 쪽에 있든 자동 확정하지 않습니다.
이 상태는 필드별 추천 결과이며, 실제 변환 코드 생성이나 일대일 매핑 저장은 수행하지 않습니다.
여러 AS-IS 필드가 동일 TO-BE 필드를 추천받을 수 있습니다.

타입 판단은 AS-IS → TO-BE 방향이며 nullable → non-null, 정밀도 손실,
컬렉션/스칼라 차이, 구조 변경, 검증되지 않은 타입 변환은 자동 확정을 차단합니다.
문자열 파싱이나 사용자 정의 클래스의 상속 관계를 추론하지 않습니다.
1단계 스키마가 배열/List/Set을 원소 타입과 collection 플래그로 표현하므로
이 컨테이너들 사이의 구체적 변환 가능성은 구별하지 않습니다.
중첩 유사도는 평탄화된 경로 기준이며 전체 객체 그래프의 동등성을 의미하지 않습니다.

새 규칙은 `MatchingRule`을 구현하고 Spring `@Component`로 등록합니다.
고유 ID, 양수 가중치와 0~100점 `RuleScore`를 반환하면 서비스가 자동으로 집계합니다.
`blocksAutoMatch`로 검토 조건을 추가할 수 있습니다.

테스트: `./gradlew test` (Windows: `.\gradlew.bat test`).

## Spring AI 검토

Spring AI 1.1.0의 OpenAI ChatModel을 사용합니다. `OPENAI_API_KEY` 환경변수를 설정하면
AI 검토가 활성화됩니다. 키가 없거나 `MAPPING_AI_ENABLED=false`이면 외부 호출 없이
규칙 결과와 AI 사용 불가 사유를 반환합니다. 키는 파일에 저장하지 않습니다.
`OPENAI_MODEL` 기본값은 `gpt-4o-mini`이며, JSON Schema 응답을 지원하는 모델을 사용해야 합니다.
현재 옵션은 temperature=0, maxTokens=1200이므로 reasoning 모델로 변경할 때는 옵션 조정이 필요합니다.

호출 대상은 `REVIEW_REQUIRED`이고 최고 점수가 `mapping.ai.minimum-score`(기본 40) 이상인 필드입니다.
자동 확정, 빈 후보, 최저 점수 미만은 호출하지 않습니다. 점수 차가 작거나 변환 검토가 필요한
고득점 필드와 일반명도 검토 대상이며, 일반명에는 추천을 하지 않는 정책을 적용합니다.
필드당 한 번 호출하고 `mapping.ai.timeout`(기본 20초)으로 연결/읽기 시간을 제한합니다.
재시도는 하지 않습니다. 여러 필드는 순차 처리하므로 총 소요 시간은 필드 수에 비례합니다.

전달 데이터는 해당 AS-IS 필드의 정규화 정보와 상위 3개 후보의 정규화 정보·점수·규칙 근거입니다.
원본 Java 파일, 전체 스키마, 다른 필드는 전달하지 않습니다. 필드 주석과 어노테이션은 포함되며,
시스템 프롬프트에서 이를 명령이 아닌 데이터로 취급하도록 지정합니다.

`aiSuggestions` 각 항목:

```json
{
  "source": {"className": "sample.Old", "path": "saleAmount"},
  "suggestion": {
    "recommendedTarget": {"className": "sample.New", "path": "amount"},
    "conversionType": "DIRECT",
    "confidence": 0.9,
    "reasons": ["두 필드의 주석이 판매 금액을 나타냅니다."],
    "reviewRequired": false
  },
  "call": {
    "model": "gpt-4o-mini",
    "inputTokens": 200,
    "outputTokens": 70,
    "totalTokens": 270,
    "responseTimeMs": 1200,
    "success": true,
    "errorCode": null
  }
}
```

응답은 strict JSON Schema로 요청하고 `AiMappingSuggestion`으로 역직렬화한 뒤 검증합니다.
추천은 전송한 후보에만 한정하며, 비어 있는 근거 또는 `value1` 같은 일반명은 서버에서도
`recommendedTarget=null`, `UNKNOWN`, 신뢰도 0, 검토 필요로 바꿉니다.
신뢰도는 0~1입니다. 변환 타입은 `DIRECT`, `NUMERIC_CONVERSION`, `STRING_PARSE`,
`FORMAT`, `COLLECTION_MAPPING`, `CUSTOM`, `UNKNOWN`입니다.
변환, 0.85 미만 신뢰도 또는 규칙 차단 근거가 있으면 검토 필요를 강제합니다.
근거 문장의 실제 의미가 올바른지는 기계적으로 보장하지 않으며 AI 결과는 규칙 결과를 덮어쓰지 않습니다.

타임아웃·API 오류·잘못된 JSON·범위 밖 대상·비정상 신뢰도·미완료 응답은 해당 필드만 실패로
처리하고 나머지 처리를 계속합니다. 규칙 결과는 그대로 반환합니다.
각 호출의 모델, 입력/출력/전체 토큰 수, 처리시간, 성공 여부, 오류 종류를 INFO 로그와 응답에 기록합니다.
사용량을 받지 못하면 토큰 수는 null이며, 응답을 받은 후 검증에 실패한 경우 사용량은 보존합니다.
success는 유효 응답 처리 성공 여부로, 정상적인 추천 거절도 true입니다.
키 없음/비활성화는 success=false, errorCode=AI_DISABLED_OR_MISSING_KEY입니다.
로그에는 API 키, 프롬프트, 응답 본문, 공급자 예외 메시지를 기록하지 않습니다.

테스트는 ChatModel 모킹과 비활성화 설정을 사용하므로 실제 OpenAI 호출이나 API 키가 필요 없습니다.
