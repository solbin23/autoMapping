# 승인 기반 Mapper 생성

전체 호출 순서는 [매핑 시스템 시퀀스 다이어그램](docs/architecture/mapping-sequence.md)에서 확인할 수 있습니다.
HTTP 상태와 고정 오류 코드는 [API 오류 응답](docs/api-errors.md)에서 확인할 수 있습니다.

분석 추천과 사용자 최종 결정은 별도 객체로 저장됩니다. 추천 점수나 AI의
`reviewRequired=false`는 사용자 승인을 대신하지 않습니다. 생성기는 `APPROVED` 결정만 사용합니다.
현재 프로젝트 저장소는 프로세스 메모리이므로 재시작하면 프로젝트·결정이 사라집니다.
생성 코드는 DB에 저장하지 않으며 응답의 파일 내용과 ZIP으로 제공합니다.

## API 순서

1. `POST /api/v1/mapping-projects`: `asIsFiles`, `toBeFiles` multipart 업로드.
   프로젝트 `id`, `revision`, 변경하지 않는 `recommendations`, 별도 `decisions`를 반환합니다.
   모든 결정의 초기 상태는 `UNMAPPED`입니다. 기존 `/api/v1/schemas/analyze`도 유지됩니다.
2. `GET /api/v1/mapping-projects/{id}`: 추천과 현재 결정 조회.
3. `PUT /api/v1/mapping-projects/{id}/decisions`: 필드별 결정 저장.
4. `POST /api/v1/mapping-projects/{id}/generate`: 승인된 결정으로 파일 생성 및 컴파일 검증.
5. `GET /api/v1/mapping-projects/{id}/mapping-plan.xlsx?revision={revision}`:
   추천과 최종 결정을 비교할 수 있는 엑셀 파일 다운로드.
6. `POST /api/v1/mapping-projects/{id}/download`: Mapper, JUnit, 엑셀과 컴파일 보고서를 ZIP으로 다운로드.
7. `POST /api/v1/mapping-projects/{id}/execute`: 승인된 결정으로 외부 시스템의 AS-IS JSON을 TO-BE JSON으로 변환.

결정 요청 예시:

```json
{
  "revision": 0,
  "decision": {
    "source": {"className": "legacy.Old", "path": "saleAmount"},
    "status": "APPROVED",
    "target": {"className": "modern.New", "path": "amount"},
    "conversionType": "STRING_PARSE"
  }
}
```

| 상태 | 의미 | 생성에 포함 |
| --- | --- | --- |
| UNMAPPED | 미결정 또는 매핑 해제 | 아니오 |
| MODIFIED | 사용자가 수정한 매핑 초안 | 아니오; 다시 APPROVED 요청 필요 |
| APPROVED | 사용자가 대상과 변환을 명시적으로 승인 | 예 |
| REJECTED | 추천을 거절 | 아니오 |

`APPROVED`, `MODIFIED`는 알려진 대상 필드와 변환 타입이 필수입니다.
`UNMAPPED`, `REJECTED`의 target과 conversionType은 null이어야 합니다.
추천 후보 외에도 업로드한 TO-BE에 실제 존재하는 필드로 수정할 수 있습니다.
결정 변경마다 revision이 증가합니다. 변경 및 생성 요청은 최신 revision을 보내야 하며,
오래된 revision은 409로 거부합니다. 생성과 실행은 해당 revision의 불변 스냅샷을 사용합니다.

생성/다운로드 요청:

```json
{"revision": 1, "packageName": "generated", "className": "ApprovedMapper"}
```

기본 패키지 VO를 다룰 때 packageName은 빈 문자열로 지정합니다.
generate 응답에는 projectId, revision, files(path/content), compilation(success/errors)가 있습니다.
컴파일 실패도 HTTP 200으로 파일과 진단을 반환합니다. 예:

```json
{
  "success": false,
  "errors": [{
    "file": "sources/as-is/legacy/Old.java",
    "line": 4,
    "column": 13,
    "code": "compiler.err.cant.resolve.location",
    "message": "cannot find symbol ..."
  }]
}
```

생성 전 매핑 검증 실패는 `MAPPING_VALIDATION`과 행·열 -1을 반환합니다.
승인 매핑 없음은 422, 잘못된 요청은 400, 존재하지 않는 프로젝트는 404입니다.
ZIP에는 Mapper, JUnit 5 테스트, 원본 VO, 독립 실행용 build.gradle, README.txt,
`mapping-plan.xlsx`, compilation-report.json이 들어갑니다. 응답 헤더
`X-Compilation-Success`로 성공 여부를 확인합니다.

`mapping-plan.xlsx`의 `매핑 계획` 시트에는 AS-IS 필드·타입, 규칙 기반 최고 추천과 점수,
사용자가 확정한 상태, 최종 TO-BE 필드·타입, 변환 타입, Mapper 포함 여부가 기록됩니다.
`내보내기 정보` 시트에는 프로젝트 ID, revision, 승인된 매핑 수가 기록됩니다.
엑셀과 Mapper는 동일한 `MappingProject` revision에서 생성되므로 서로 다른 결정 상태를
표현하지 않습니다. 테스트는 엑셀 파일을 생성하지 않고 API 응답의 XLSX 구조만 검증합니다.
압축을 푼 후 JDK 21과 설치된 Gradle로 `gradle test`를 실행할 수 있습니다.
VO에서 사용하는 외부 라이브러리는 build.gradle에 추가해야 합니다.

## 실제 JSON 데이터 변환

외부 시스템은 Mapper ZIP을 프로젝트에 포함하지 않아도 승인된 매핑을 서버에서 바로 실행할 수 있습니다.
실행 단계는 OpenAI, 규칙 재분석, 코드 생성 또는 컴파일을 호출하지 않습니다.

```http
POST /api/v1/mapping-projects/{id}/execute
Content-Type: application/json
```

```json
{
  "revision": 1,
  "sourceClass": "legacy.OldOrder",
  "targetClass": "modern.NewOrder",
  "sourceData": {
    "saleAmount": "12500",
    "internalMemo": "승인되지 않아 복사되지 않음"
  }
}
```

```json
{
  "projectId": "프로젝트 UUID",
  "revision": 1,
  "sourceClass": "legacy.OldOrder",
  "targetClass": "modern.NewOrder",
  "targetData": {
    "amount": 12500
  },
  "appliedMappings": 1
}
```

`sourceClass`와 `targetClass`는 업로드 소스의 정규 클래스명입니다. 한 프로젝트에 여러 루트 클래스가
있을 수 있으므로 실행할 클래스 쌍을 명시합니다. 같은 깊이의 중첩 배열을 인덱스별로 변환하며 빈 배열과
null 배열을 보존합니다. 승인되지 않은 필드는 `sourceData`에 있어도 결과에 포함하지 않습니다.
실행 값의 타입·범위·필수값이 승인 계획과 맞지 않으면 422 `MAPPING_EXECUTION_FAILED`를 반환합니다.

## 생성 범위와 검증

승인 매핑을 AS-IS/TO-BE 클래스 쌍으로 묶어 `map1`, `map2` 형태의 타입 지정 메서드를 생성합니다.
private 필드는 reflection으로 읽고 쓰므로 getter/setter는 필수가 아닙니다.
대상 VO·중첩 VO는 기본 생성자와 변경 가능한 필드가 필요하며 reflection 접근이 허용되어야 합니다.
중첩 객체와 배열/List/Set의 원소를 지원합니다. 컬렉션 중첩 깊이가 동일해야 하며,
원소 타입은 구체 클래스여야 합니다. 인터페이스 또는 ArrayList/HashSet과 호환되는 컨테이너를 사용합니다.
임의 컨테이너 구현체, record/불변 객체, 생성자 주입, 집계·분할 변환은 지원하지 않습니다.
같은 클래스 쌍에서 여러 소스가 동일 대상 필드에 쓰는 매핑은 거절합니다.

지원 변환:

- DIRECT: 같은 타입 또는 primitive/wrapper 대응 타입.
- NUMERIC_CONVERSION: 기본 숫자, BigInteger, BigDecimal 사이 변환.
- STRING_PARSE: 문자열을 숫자·boolean·char로 변환.
- FORMAT: 기본 값의 문자열 표현. 사용자 지정 날짜/숫자 패턴은 미지원.
- COLLECTION_MAPPING: 동일 원소 타입 컬렉션 매핑. 원소 변환은 위 변환 타입으로 지정.

UNKNOWN/CUSTOM은 구체 구현이 없으므로 생성 전에 진단을 반환합니다.
정수 범위 초과와 소수부 손실, 잘못된 boolean/char, null→primitive는 예외로 처리합니다.
float/double의 유한 범위는 검사하지만 부동소수점 정밀도 손실까지 제거하지는 않습니다.
전체 source가 null이면 target도 null입니다. 사용자 정의 객체의 DIRECT는 참조를 복사합니다.

서버는 `JavaCompiler`로 VO·Mapper·JUnit 소스를 함께 컴파일합니다.
애노테이션 프로세서는 `-proc:none`으로 끄며 업로드 코드·생성 코드를 서버에서 실행하지 않습니다.
바이트코드는 메모리에서만 생성되고 보관하지 않습니다. JDK 21 이상이 필요합니다.
컴파일 의존성은 서버의 파일 기반 실행 클래스패스입니다. `bootRun` 또는 펼친 클래스패스로 실행하세요.
외부 의존성 누락, 같은 정규명을 가진 AS-IS/TO-BE 클래스, 패키지 접근 문제도 진단 대상입니다.
컴파일 성공은 런타임 reflection 접근이나 비즈니스 로직의 정확성을 보장하지 않습니다.

자동 생성된 JUnit 테스트는 null 입력, 지원 타입의 승인 매핑 예제, 잘못된 숫자, 정수 overflow를 검사합니다.
프로젝트 테스트에서는 생성된 테스트를 실제로 컴파일·실행해 기본 변환 및 중첩 컬렉션을 확인합니다.
또한 상태 전환·추천 불변성·미승인 제외·컴파일 진단·업로드부터 ZIP 다운로드까지 검증합니다.
전체 테스트: `.\gradlew.bat test`. OpenAI는 모킹/비활성화하므로 실제 API 호출은 없습니다.
