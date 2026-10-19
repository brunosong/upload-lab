package com.example.uploadlab.chunked.domain;

import java.time.LocalDateTime;

/**
 * 조각을 다 합쳐서 만든 파일 한 건.
 */
public record CompletedUpload(
        String id,              // 업로드 세션 ID를 그대로 쓴다
        String originalName,
        long size,              // 합친 파일의 바이트 수
        int totalChunks,
        String sha256,          // 합치면서 계산한 해시
        long elapsedMillis,     // 세션 시작부터 완료까지 걸린 시간
        LocalDateTime completedAt
) {
}
