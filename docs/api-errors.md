# API 오류 응답

모든 API 오류는 HTTP 상태와 함께 다음 JSON 형식으로 반환됩니다.

```json
{
  "timestamp": "2026-10-06T02:23:14.123Z",
  "status": 409,
  "error": "Conflict",
  "code": "PROJECT_REVISION_CONFLICT",
  "message": "Project revision conflict for ...: expected 9 but current revision is 0",
  "path": "/api/v1/mapping-projects/{id}/decisions"
}
```

클라이언트는 변경될 수 있는 `message` 대신 고정된 `code`로 처리 분기를 만듭니다.

| HTTP 상태 | 오류 코드 | 발생 조건 | 권장 처리 |
| --- | --- | --- | --- |
| 400 | `INVALID_REQUEST` | 파일명, 매핑 조합, Java 식별자 등 입력 검증 실패 | 입력값 수정 |
| 400 | `MISSING_REQUEST_VALUE` | 필수 multipart 또는 query parameter 누락 | 누락 값 추가 |
| 400 | `MALFORMED_REQUEST` | 읽을 수 없는 JSON 또는 Bean Validation 실패 | 요청 형식 수정 |
| 400 | `SCHEMA_PARSE_ERROR` | 업로드한 Java 소스 구문 분석 실패 | 오류 파일 수정 |
| 404 | `MAPPING_PROJECT_NOT_FOUND` | 존재하지 않는 프로젝트 ID | 프로젝트를 새로 생성하거나 ID 확인 |
| 409 | `PROJECT_REVISION_CONFLICT` | 요청 revision과 현재 revision 불일치 | 프로젝트 재조회 후 변경 재적용 |
| 413 | `UPLOAD_TOO_LARGE` | multipart 용량 제한 초과 | 파일 크기 또는 개수 축소 |
| 415 | `UNSUPPORTED_MEDIA_TYPE` | 지원하지 않는 Content-Type | 엔드포인트의 요청 형식 사용 |
| 422 | `NO_APPROVED_MAPPINGS` | 생성할 승인 매핑이 없음 | 하나 이상의 매핑 승인 |
| 500 | `INTERNAL_ERROR` | 예상하지 못한 서버 오류 | 서버 로그 확인 후 재시도 |

Mapper 컴파일 실패와 매핑 생성 검증 오류는 생성 작업의 정상 결과이므로 HTTP 200의
`GenerationResult.compilation`에 파일, 행, 열, 오류 코드와 함께 반환됩니다.
