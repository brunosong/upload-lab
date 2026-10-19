package com.example.uploadlab.chunked.adapter.out.storage;

import com.example.uploadlab.chunked.application.out.ChunkStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * 조각을 로컬 디스크에 쓴다.
 *
 *   uploads/chunked/{uploadId}/0.part
 *   uploads/chunked/{uploadId}/1.part
 *   uploads/chunked/{uploadId}/1.part.tmp   ← 받는 중이거나, 검사 전인 조각
 */
@Service
public class LocalDiskChunkStorageAdapter implements ChunkStoragePort {

    private static final int BUFFER_SIZE = 8192;   // 조각이 5MB여도 메모리에는 이 통 하나만 있다

    private final Path baseDir;

    public LocalDiskChunkStorageAdapter(@Value("${lab.upload.dir}") String uploadDir) {
        this.baseDir = Path.of(uploadDir, "chunked");
    }

    @Override
    public long writeTemp(String uploadId, int index, InputStream in, long limit) throws IOException {
        Files.createDirectories(baseDir.resolve(uploadId));

        byte[] buffer = new byte[BUFFER_SIZE];
        long total = 0;

        try (OutputStream out = Files.newOutputStream(tempPath(uploadId, index))) {
            // limit + 1 바이트까지만 읽는다. 조각 자리에 10GB를 보내도 디스크에 10GB가 쌓이지 않는다
            while (total <= limit) {
                int n = in.read(buffer, 0, (int) Math.min(buffer.length, limit + 1 - total));
                if (n == -1) break;
                out.write(buffer, 0, n);
                total += n;
            }
        }
        return total;
    }

    @Override
    public void commit(String uploadId, int index) throws IOException {
        // 이름만 바꾸는 거라 빠르고, 중간에 끊겨도 반쯤 쓴 .part가 생기지 않는다
        Files.move(tempPath(uploadId, index), partPath(uploadId, index),
                StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
    }

    @Override
    public void discard(String uploadId, int index) throws IOException {
        Files.deleteIfExists(tempPath(uploadId, index));
    }

    private Path partPath(String uploadId, int index) {
        return baseDir.resolve(uploadId).resolve(index + ".part");
    }

    private Path tempPath(String uploadId, int index) {
        return baseDir.resolve(uploadId).resolve(index + ".part.tmp");
    }
}
