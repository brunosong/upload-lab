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
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.stream.Stream;

/**
 * 조각을 로컬 디스크에 쓰고, 다 모이면 하나로 합친다.
 *
 *   uploads/chunked/{uploadId}/0.part
 *   uploads/chunked/{uploadId}/1.part
 *   uploads/chunked/{uploadId}/1.part.tmp   ← 받는 중이거나, 검사 전인 조각
 *   uploads/chunked-{uploadId}.dat          ← 합친 결과. 합친 뒤 조각 폴더는 지운다
 */
@Service
public class LocalDiskChunkStorageAdapter implements ChunkStoragePort {

    private static final int BUFFER_SIZE = 8192;   // 조각이 5MB여도 메모리에는 이 통 하나만 있다

    private final Path uploadDir;   // 합친 파일을 두는 곳
    private final Path baseDir;     // 조각을 모아 두는 곳

    public LocalDiskChunkStorageAdapter(@Value("${lab.upload.dir}") String uploadDir) {
        this.uploadDir = Path.of(uploadDir);
        this.baseDir = this.uploadDir.resolve("chunked");
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

    @Override
    public AssembledFile assemble(String uploadId, int totalChunks) throws IOException {
        Path target = uploadDir.resolve("chunked-" + uploadId + ".dat");   // streaming-{id}.dat 와 같은 자리, 같은 규칙
        Path temp = uploadDir.resolve("chunked-" + uploadId + ".dat.tmp");

        MessageDigest md = sha256();

        // DigestOutputStream: 쓰는 동안 해시를 같이 계산한다. 합친 파일을 다시 읽을 필요가 없다
        try (OutputStream out = new DigestOutputStream(Files.newOutputStream(temp), md)) {
            for (int i = 0; i < totalChunks; i++) {
                Files.copy(partPath(uploadId, i), out);   // 조각 하나를 조금씩 흘려 붙인다. 통째로 읽지 않는다
            }
        }

        Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        return new AssembledFile(Files.size(target), HexFormat.of().formatHex(md.digest()));
    }

    @Override
    public void deleteChunks(String uploadId) throws IOException {
        Path dir = baseDir.resolve(uploadId);
        if (!Files.exists(dir)) return;

        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.toList()) {
                Files.delete(file);
            }
        }
        Files.delete(dir);
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // 자바라면 SHA-256은 항상 있다
        }
    }

    private Path partPath(String uploadId, int index) {
        return baseDir.resolve(uploadId).resolve(index + ".part");
    }

    private Path tempPath(String uploadId, int index) {
        return baseDir.resolve(uploadId).resolve(index + ".part.tmp");
    }
}
