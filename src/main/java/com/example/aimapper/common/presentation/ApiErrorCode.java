package com.example.aimapper.common.presentation;

/**
 * API 소비자가 오류 메시지 문구와 무관하게 처리 분기를 만들 수 있는 고정 오류 코드이다.
 */
public enum ApiErrorCode {
    INVALID_REQUEST,
    MISSING_REQUEST_VALUE,
    MALFORMED_REQUEST,
    SCHEMA_PARSE_ERROR,
    MAPPING_PROJECT_NOT_FOUND,
    PROJECT_REVISION_CONFLICT,
    NO_APPROVED_MAPPINGS,
    UPLOAD_TOO_LARGE,
    UNSUPPORTED_MEDIA_TYPE,
    INTERNAL_ERROR
}
