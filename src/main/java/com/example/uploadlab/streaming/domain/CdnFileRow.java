package com.example.uploadlab.streaming.domain;

/**
 * 업체 목록 CSV의 한 행. CDN에 있는 파일 하나.
 */
public record CdnFileRow(
        String path,   // 예: /img/2026/10/a.jpg
        long size      // 바이트
) {
}
