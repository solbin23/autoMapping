# 매핑 시스템 시퀀스 다이어그램

이 문서는 현재 구현된 VO 분석, 후보 추천, 사용자 승인, Mapper·JUnit·엑셀 생성 흐름을 설명합니다.
실제 업무 데이터 변환 API는 아직 구현되지 않았으며 문서 마지막에 향후 흐름으로 분리했습니다.

## 1. VO 분석과 후보 생성

`POST /api/v1/mapping-projects`는 AS-IS와 TO-BE Java 파일을 분석하고 필드별 후보를 생성합니다.
AI에는 전체 소스나 전체 스키마를 보내지 않고 `REVIEW_REQUIRED`이면서 최고 후보 점수가 설정값 이상인
필드의 정규화 정보와 상위 3개 후보만 전달합니다.

![VO 분석과 후보 생성 시퀀스](images/01-analysis-candidates.svg)

<details>
<summary>Mermaid 원본 보기</summary>

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant API as MappingProjectController
    participant Project as MappingProjectService
    participant Analysis as SchemaAnalysisService
    participant Parser as JavaParserSchemaAnalyzer
    participant Rules as FieldMatchingService
    participant AIService as AiMappingService
    participant OpenAI as OpenAI ChatModel
    participant Store as 메모리 저장소

    User->>API: POST /api/v1/mapping-projects<br/>AS-IS, TO-BE Java 파일
    API->>Project: create(asIsFiles, toBeFiles)
    Project->>Analysis: analyze(asIsFiles, toBeFiles)

    Analysis->>Parser: AS-IS 소스 분석
    Parser-->>Analysis: AS-IS SchemaSnapshot
    Analysis->>Parser: TO-BE 소스 분석
    Parser-->>Analysis: TO-BE SchemaSnapshot

    Analysis->>Rules: 모든 필드 후보 평가
    Rules-->>Analysis: FieldMatch 목록<br/>AUTO_MATCHED / REVIEW_REQUIRED / NO_CANDIDATE

    Analysis->>AIService: suggest(matches)
    loop AI 검토 대상 필드
        AIService->>OpenAI: 정규화된 필드 + 상위 3개 후보
        alt AI 호출과 응답 검증 성공
            OpenAI-->>AIService: 구조화된 AiMappingSuggestion
        else 키 없음, 타임아웃, 잘못된 응답
            OpenAI--xAIService: 호출 또는 검증 실패
            AIService->>AIService: recommendedTarget=null<br/>규칙 결과 유지
        end
    end
    AIService-->>Analysis: AI 검토 결과
    Analysis-->>Project: SchemaComparisonResult

    Project->>Project: 모든 소스 필드를 UNMAPPED로 초기화
    Project->>Store: 프로젝트, 추천, 결정, revision=0 저장
    Project-->>API: MappingProject
    API-->>User: 프로젝트 ID, 추천 후보, AI 제안, 결정 상태
```

</details>

규칙 결과와 AI 제안은 추천 정보입니다. `AUTO_MATCHED`나 높은 AI 신뢰도만으로 최종 결정이
`APPROVED`가 되지는 않습니다.

## 2. 사용자 결정 저장

`PUT /api/v1/mapping-projects/{id}/decisions`는 추천과 분리된 최종 결정을 저장합니다.
클라이언트가 보낸 revision이 현재 revision과 다르면 오래된 화면의 변경으로 판단해 거부합니다.

![사용자 결정 저장 시퀀스](images/02-user-decision.svg)

<details>
<summary>Mermaid 원본 보기</summary>

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant API as MappingProjectController
    participant Project as MappingProjectService
    participant Store as 메모리 저장소

    User->>API: PUT /{id}/decisions<br/>revision + MappingDecision
    API->>Project: decide(id, expectedRevision, decision)
    Project->>Store: 현재 프로젝트 조회
    Store-->>Project: MappingProject

    alt revision 불일치
        Project-->>API: IllegalArgumentException
        API-->>User: 400 Bad Request<br/>프로젝트를 다시 조회해야 함
    else source/target 또는 상태 조합이 잘못됨
        Project-->>API: IllegalArgumentException
        API-->>User: 400 Bad Request<br/>결정 검증 오류
    else 유효한 결정
        Project->>Project: 해당 필드 결정 교체
        Project->>Store: revision + 1로 저장
        Project-->>API: 변경된 MappingProject
        API-->>User: 최신 revision과 결정 목록
    end
```

</details>

상태별 코드 생성 여부는 다음과 같습니다.

| 상태 | 의미 | Mapper 포함 |
| --- | --- | --- |
| `UNMAPPED` | 아직 결정하지 않음 | 아니오 |
| `MODIFIED` | 추천을 수정했지만 아직 승인하지 않음 | 아니오 |
| `REJECTED` | 매핑하지 않기로 결정 | 아니오 |
| `APPROVED` | 대상 필드와 변환 방식을 확정 | 예 |

## 3. Mapper 생성과 컴파일 검증

`POST /api/v1/mapping-projects/{id}/generate`는 JSON으로 생성 파일과 컴파일 결과를 반환합니다.
생성기는 동일 revision의 `APPROVED` 결정만 사용합니다.

![Mapper 생성과 컴파일 검증 시퀀스](images/03-generation-compilation.svg)

<details>
<summary>Mermaid 원본 보기</summary>

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant API as MappingProjectController
    participant Generator as MapperGenerationService
    participant Project as MappingProjectService
    participant Compiler as JavaCompilationVerifier

    User->>API: POST /{id}/generate<br/>revision, packageName, className
    API->>Generator: generate(id, revision, packageName, className)
    Generator->>Project: stored(id)
    Project-->>Generator: 프로젝트와 업로드 소스

    alt revision 불일치 또는 승인 매핑 없음
        Generator-->>API: IllegalArgumentException
        API-->>User: 400 Bad Request
    else 매핑 검증 실패
        Generator->>Generator: 중복 대상·미지원 변환 검증
        Generator-->>API: GenerationResult<br/>compilation.success=false
        API-->>User: MAPPING_VALIDATION 오류
    else 유효한 승인 매핑
        Generator->>Generator: Java Mapper 생성
        Generator->>Generator: JUnit 테스트 생성
        Generator->>Compiler: VO + Mapper + JUnit 컴파일
        Compiler-->>Generator: 성공 또는 파일·행·열·원인
        Generator-->>API: GenerationResult
        API-->>User: 생성 파일과 컴파일 결과 JSON
    end
```

</details>

컴파일 검증은 코드를 실행하지 않으며 애노테이션 프로세서도 비활성화합니다. 컴파일이 성공해도
런타임 reflection 접근이나 실제 업무 의미의 정확성까지 보장하지는 않습니다.

## 4. Mapper와 매핑 계획 다운로드

`POST /api/v1/mapping-projects/{id}/download`는 Mapper 구현과 매핑 계획 엑셀을 하나의 ZIP으로
반환합니다. `GET /api/v1/mapping-projects/{id}/mapping-plan.xlsx`는 엑셀만 반환합니다.

![Mapper와 매핑 계획 다운로드 시퀀스](images/04-download.svg)

<details>
<summary>Mermaid 원본 보기</summary>

```mermaid
sequenceDiagram
    autonumber
    actor User as 사용자
    participant API as MappingProjectController
    participant Generator as MapperGenerationService
    participant Compiler as JavaCompilationVerifier
    participant Project as MappingProjectService
    participant Excel as MappingPlanExcelWriter

    User->>API: POST /{id}/download<br/>revision, packageName, className
    API->>Generator: generate(id, revision, packageName, className)
    Generator->>Compiler: 생성 소스 컴파일 검증
    Compiler-->>Generator: CompilationResult
    Generator-->>API: Mapper, JUnit, VO, 빌드 파일

    API->>Project: get(id)
    Project-->>API: 동일 revision의 MappingProject
    API->>Excel: write(project)
    Excel-->>API: mapping-plan.xlsx bytes

    API->>API: 생성 파일 + XLSX + 컴파일 보고서 ZIP 구성
    API-->>User: approved-mapper.zip<br/>X-Compilation-Success 헤더

    opt 엑셀만 다운로드
        User->>API: GET /{id}/mapping-plan.xlsx?revision=N
        API->>Project: get(id) 및 revision 검증
        Project-->>API: MappingProject
        API->>Excel: write(project)
        Excel-->>API: XLSX bytes
        API-->>User: mapping-plan-{id}-rN.xlsx
    end
```

</details>

ZIP에는 다음 파일이 포함됩니다.

```text
src/main/java/.../ApprovedMapper.java
src/test/java/.../ApprovedMapperTest.java
sources/as-is/...
sources/to-be/...
mapping-plan.xlsx
compilation-report.json
build.gradle
README.txt
```

## 5. 외부 시스템 실데이터 변환 흐름

`POST /api/v1/mapping-projects/{id}/execute`는 저장된 승인 계획으로 AS-IS JSON을 TO-BE JSON으로
변환합니다. 실행 단계에서는 AI, 규칙 재분석, 코드 생성과 컴파일을 호출하지 않습니다.

![외부 시스템 실데이터 변환 시퀀스](images/05-json-execution.svg)

<details>
<summary>Mermaid 원본 보기</summary>

```mermaid
sequenceDiagram
    autonumber
    actor Client as 업무 시스템
    participant API as MappingExecutionController
    participant Service as MappingExecutionService
    participant Store as MappingProjectService
    participant Engine as JsonMappingEngine

    Client->>API: POST /{id}/execute<br/>revision + 클래스 쌍 + AS-IS JSON
    API->>Service: execute(id, revision, classes, sourceData)
    Service->>Store: get(id)
    Store-->>Service: 현재 MappingProject

    alt 프로젝트 없음 또는 revision 불일치
        Service-->>API: 404 또는 409 오류
        API-->>Client: 고정 오류 코드
    else 현재 revision
        Service->>Engine: 승인 결정 + sourceData
        alt 변환 성공
            Engine-->>Service: targetData + 적용 매핑 수
            Service-->>API: MappingExecutionResult
            API-->>Client: 200 OK + TO-BE JSON
        else 타입·경로·필수값 오류
            Engine-->>Service: MappingExecutionException
            Service-->>API: 실행 실패
            API-->>Client: 422 MAPPING_EXECUTION_FAILED
        end
    end
```

</details>

현재 `MappingProjectService`는 프로젝트와 결정을 메모리에 저장하므로 서버 재시작 시 사라집니다.
실데이터 실행 API를 운영 배포하기 전에는 승인 계획을 DB 또는 버전 관리 파일에 영구 저장해야 합니다.
