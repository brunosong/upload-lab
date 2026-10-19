package com.example.uploadlab.streaming.domain;

import java.util.List;

/**
 * CSV를 받으면서 끝까지 읽은 결과.
 */
public record CsvReadResult(
        String originalName,
        List<String> header,  // 첫 행
        long rows,            // 헤더를 뺀 행 수
        long badRows,         // 칸 수가 헤더와 다른 행 수
        long bytes,           // 네트워크에서 받은 바이트 수
        long elapsedMillis,
        long maxHeapUsedMb    // 읽는 동안 잰 힙 사용량 중 가장 큰 값
) {
}
