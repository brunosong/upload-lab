package com.example.uploadlab.chunked.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 도메인 규칙만 본다. 스프링도 파일도 없다.
 */
class UploadSessionTest {

    @Test
    void 크기가_0이면_시작할_수_없다() {
        assertThatThrownBy(() -> start(0, 4)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 나누어떨어지지_않으면_마지막_조각만_작다() {
        UploadSession s = start(10, 4);

        assertThat(s.totalChunks()).isEqualTo(3);
        assertThat(s.expectedSizeOf(0)).isEqualTo(4);
        assertThat(s.expectedSizeOf(2)).isEqualTo(2);
    }

    @Test
    void 나누어떨어지면_조각_수가_딱_맞다() {
        UploadSession s = start(8, 4);

        assertThat(s.totalChunks()).isEqualTo(2);
        assertThat(s.expectedSizeOf(1)).isEqualTo(4);
    }

    @Test
    void 큰_파일에서도_마지막_조각_크기가_넘치지_않는다() {
        long size = 10L * 1024 * 1024 * 1024 + 1;   // 10GB + 1바이트
        UploadSession s = start(size, 5 * 1024 * 1024);

        assertThat(s.expectedSizeOf(s.totalChunks() - 1)).isEqualTo(1);
    }

    @Test
    void 범위_밖_번호는_거부한다() {
        UploadSession s = start(10, 4);

        assertThatThrownBy(() -> s.expectedSizeOf(-1)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> s.expectedSizeOf(3)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 같은_조각을_두_번_받아도_한_번으로_센다() {
        UploadSession s = start(10, 4);

        s.markReceived(0, 4);
        s.markReceived(0, 4);

        assertThat(s.receivedCount()).isEqualTo(1);
        assertThat(s.missingChunks()).containsExactly(1, 2);
    }

    @Test
    void 조각이_빠지면_완료할_수_없다() {
        UploadSession s = start(10, 4);
        s.markReceived(0, 4);
        s.markReceived(2, 2);

        assertThatThrownBy(s::complete)
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[1]");
        assertThat(s.status()).isEqualTo(UploadSession.Status.UPLOADING);
    }

    @Test
    void 완료한_뒤에는_조각을_더_받지_않고_다시_완료할_수도_없다() {
        UploadSession s = start(10, 4);
        s.markReceived(0, 4);
        s.markReceived(1, 4);
        s.markReceived(2, 2);
        s.complete();

        assertThat(s.status()).isEqualTo(UploadSession.Status.COMPLETED);
        assertThatThrownBy(() -> s.markReceived(0, 4)).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(s::complete).isInstanceOf(IllegalStateException.class);
    }

    private UploadSession start(long totalSize, int chunkSize) {
        return UploadSession.start("id", "a.bin", totalSize, chunkSize, LocalDateTime.now());
    }
}
