package com.example.uploadlab.streaming.domain;

import java.time.LocalDateTime;

/**
 * 업로드가 끝난 파일 한 건.
 * 스프링도, 파일 시스템도 모르는 순수한 값이다.
 */
public record UploadedFile(
        String id,              // 서버가 만든 ID. 저장 파일 이름으로도 쓴다
        String originalName,    // 사용자가 올린 원래 이름. 기록만 하고 저장 경로에는 쓰지 않는다
        long size,              // 받은 바이트 수
        String sha256,          // 받으면서 계산한 해시
        long elapsedMillis,     // 받는 데 걸린 시간
        LocalDateTime uploadedAt
) {
}
