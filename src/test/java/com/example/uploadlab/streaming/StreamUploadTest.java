package com.example.uploadlab.streaming;

import com.example.uploadlab.streaming.adapter.out.record.InMemoryUploadRecordAdapter;
import com.example.uploadlab.streaming.adapter.out.storage.LocalDiskStorageAdapter;
import com.example.uploadlab.streaming.application.StreamUploadService;
import com.example.uploadlab.streaming.domain.UploadedFile;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 스프링 없이 포트에 어댑터를 직접 끼워서 돌린다.
 * 헥사고날 구조라 이렇게 조립해서 테스트할 수 있다.
 */
class StreamUploadTest {

    @TempDir
    Path tempDir;

    @Test
    void 받은_내용이_그대로_저장되고_해시가_맞다() throws Exception {
        StreamUploadService service = new StreamUploadService(
                new LocalDiskStorageAdapter(tempDir.toString()),
                new InMemoryUploadRecordAdapter());

        byte[] content = "path\tsize\n/a.jpg\t100\n".getBytes();

        UploadedFile file = service.upload("목록.tsv", new ByteArrayInputStream(content));

        Path saved = tempDir.resolve("streaming-" + file.id() + ".dat");
        assertThat(Files.readAllBytes(saved)).isEqualTo(content);
        assertThat(file.size()).isEqualTo(content.length);
        assertThat(file.sha256()).isEqualTo(sha256(content));
        assertThat(file.originalName()).isEqualTo("목록.tsv");
        assertThat(service.findAll()).hasSize(1);
    }

    @Test
    void 메모리에_다_올리지_않고_큰_데이터를_받는다() throws Exception {
        StreamUploadService service = new StreamUploadService(
                new LocalDiskStorageAdapter(tempDir.toString()),
                new InMemoryUploadRecordAdapter());

        long size = 200L * 1024 * 1024;   // 200MB를 실제 배열 없이 흘려 보내는 가짜 스트림
        InputStream zeros = new InputStream() {
            long left = size;

            @Override
            public int read() {
                if (left == 0) return -1;
                left--;
                return 0;
            }

            @Override
            public int read(byte[] b, int off, int len) {
                if (left == 0) return -1;
                int n = (int) Math.min(len, left);
                java.util.Arrays.fill(b, off, off + n, (byte) 0);
                left -= n;
                return n;
            }
        };

        UploadedFile file = service.upload("zeros.bin", zeros);

        assertThat(file.size()).isEqualTo(size);
        assertThat(Files.size(tempDir.resolve("streaming-" + file.id() + ".dat"))).isEqualTo(size);
    }

    private String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }
}
