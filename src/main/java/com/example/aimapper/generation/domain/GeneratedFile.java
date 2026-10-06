package com.example.aimapper.generation.domain;

/** ZIP 또는 JSON 응답으로 돌려줄 생성 파일의 상대 경로와 텍스트 내용이다. */
public record GeneratedFile(String path, String content) {}
