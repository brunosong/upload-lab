package com.example.uploadlab.chunked.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class UploadSessionTest {

    @Test
    void 크기가_0이면_시작할_수_없다() {
        assertThatThrownBy(() -> start(0, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    private UploadSession start(long totalSize, int chunkSize) {
        return UploadSession.start("id", "a.bin", totalSize, chunkSize, LocalDateTime.now());
    }
}