package com.example.uploadlab.chunked.domain;

import java.time.LocalDateTime;
import java.util.BitSet;

/**
 * 조각 업로드 한 건의 진행 상태.
 * 몇 번 조각을 받았는지, 다 모였는지를 판단한다. 스프링도, 파일 시스템도 모른다.
 */
public class UploadSession {

    public enum Status { UPLOADING, COMPLETED }

    private final String uploadId;
    private final String originalName;
    private final long totalSize;
    private final int chunkSize;
    private final int totalChunks;
    private final BitSet received; // 받은 조각 번호
    private final LocalDateTime createdAt;
    private Status status = Status.UPLOADING;

    private UploadSession(String uploadId, String originalName, long totalSize,
                          int chunkSize, int totalChunks, LocalDateTime createdAt) {
        this.uploadId = uploadId;
        this.originalName = originalName;
        this.totalSize = totalSize;
        this.chunkSize = chunkSize;
        this.totalChunks = totalChunks;
        this.received = new BitSet(totalChunks);
        this.createdAt = createdAt;
    }

    public static UploadSession start(String uploadId, String originalName,
                                      long totalSize, int chunkSize, LocalDateTime now) {
        if (originalName == null || originalName.isBlank()) {
            throw new IllegalArgumentException("파일 이름이 없습니다.");
        }

        if (totalSize <= 0) {
            throw new IllegalArgumentException("파일 크기는 0보다 커야 합니다: " + totalSize);
        }

        if (chunkSize <= 0) {
            throw new IllegalArgumentException("조각 크기는 0보다 커야 합니다: " + chunkSize);
        }

        long chunks = (totalSize + chunkSize - 1) / chunkSize; //올림 나눗셈
        if (chunks > Integer.MAX_VALUE) {
            throw new IllegalArgumentException("조각이 너무 많습니다: " + chunks);
        }

        return new UploadSession(uploadId, originalName, totalSize, chunkSize, (int) chunks, now);
    }

    /** index 번 조각이 가져야 할 바이트 수. 마지막 조각만 chunkSize보다 작을 수 있다. */
    public long expectedSizeOf(int index) {
        checkIndex(index);
        if (index == totalChunks - 1) {
            return totalSize - (long) chunkSize * index;
        }
        return chunkSize;
    }

    /** 이 조각을 받아도 되는지만 본다. 기록은 하지 않는다. */
    public void checkCanReceive(int index, long size) {
        checkUploading();
        long expected = expectedSizeOf(index);
        if (size != expected) {
            throw new IllegalArgumentException(
                    index + "번 조각 크기가 다릅니다. 기대: " + expected + ", 받음: " + size);
        }
    }

    /** 조각을 받았다고 기록한다. 같은 번호가 다시 와도 결과는 같다 (재시도 안전) */
    public void markReceived(int index, long size) {
        checkCanReceive(index, size);
        received.set(index);
    }

    private void checkIndex(int index) {
        if (index < 0 || index >= totalChunks) {
            throw new IllegalArgumentException(
                    "조각 번호는 0 ~ " + (totalChunks - 1) + " 사이여야 합니다: " + index);
        }
    }

    private void checkUploading() {
        if (status != Status.UPLOADING) {
            throw new IllegalStateException("이미 끝난 업로드입니다: " + uploadId);
        }
    }

    public String uploadId() { return uploadId; }
    public String originalName() { return originalName; }
    public long totalSize() { return totalSize; }
    public int chunkSize() { return chunkSize; }
    public int totalChunks() { return totalChunks; }
    public int receivedCount() { return received.cardinality(); }
    public Status status() { return status; }
    public LocalDateTime createdAt() { return createdAt; }

}
