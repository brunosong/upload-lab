package com.example.uploadlab.streaming.domain;

/**
 * 텍스트를 받으면서 끝까지 읽은 결과.
 */
public record TextReadResult(
        String originalName,
        long lines,           // 읽은 줄 수
        long bytes,           // 네트워크에서 받은 바이트 수
        long elapsedMillis,
        long maxHeapUsedMb    // 읽는 동안 잰 힙 사용량 중 가장 큰 값
) {
}
