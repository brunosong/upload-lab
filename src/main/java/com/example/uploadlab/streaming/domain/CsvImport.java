package com.example.uploadlab.streaming.domain;

import java.time.LocalDateTime;

/**
 * CSV 한 번 넣기. 시작하면 IMPORTING, 끝나면 COMPLETED 나 FAILED 가 된다.
 * 바뀔 때마다 새 값을 만든다.
 */
public record CsvImport(
        String id,
        String originalName,
        Status status,
        long savedRows,       // DB에 넣은 행 수. 실패하면 지우므로 0
        long badRows,         // 형식이 틀려 건너뛴 행 수
        String error,         // 실패 이유. 성공이면 null
        LocalDateTime startedAt,
        LocalDateTime finishedAt
) {

    private static final int MAX_ERROR_LENGTH = 1000;   // csv_import.error 컬럼 길이

    public enum Status { IMPORTING, COMPLETED, FAILED }

    public static CsvImport start(String id, String originalName, LocalDateTime now) {
        return new CsvImport(id, originalName, Status.IMPORTING, 0, 0, null, now, null);
    }

    public CsvImport complete(long savedRows, long badRows, LocalDateTime now) {
        checkImporting();
        return new CsvImport(id, originalName, Status.COMPLETED, savedRows, badRows, null, startedAt, now);
    }

    public CsvImport fail(long badRows, String error, LocalDateTime now) {
        checkImporting();
        String reason = error == null ? "알 수 없는 오류" : error;
        if (reason.length() > MAX_ERROR_LENGTH) reason = reason.substring(0, MAX_ERROR_LENGTH);
        return new CsvImport(id, originalName, Status.FAILED, 0, badRows, reason, startedAt, now);
    }

    private void checkImporting() {
        if (status != Status.IMPORTING) {
            throw new IllegalStateException("이미 끝난 import 입니다: " + id + " (" + status + ")");
        }
    }
}
