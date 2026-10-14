package com.example.uploadlab.streaming.adapter.out.storage;

import com.example.uploadlab.streaming.application.port.out.FileStoragePort;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * 받은 데이터를 로컬 디스크에 흘려 쓴다.
 * 이 lab의 핵심은 save() 안의 while 문 하나다.
 */
@Component
public class LocalDiskStorageAdapter implements FileStoragePort {

    private static final int BUFFER_SIZE = 8192;   // 8KB. 파일이 1GB여도 메모리에는 이 통 하나만 있다

    private final Path baseDir;

    public LocalDiskStorageAdapter(@Value("${lab.upload.dir}") String baseDir) {
        this.baseDir = Path.of(baseDir);
    }

    @Override
    public StoredFile save(String id, InputStream in) throws IOException {
        Files.createDirectories(baseDir);
        Path target = baseDir.resolve("streaming-" + id + ".dat");   // 원래 이름은 경로에 쓰지 않는다

        MessageDigest md = sha256();
        long total = 0;

        // DigestInputStream: 읽는 동안 해시를 같이 계산한다. 파일을 다시 읽을 필요가 없다
        try (InputStream digestIn = new DigestInputStream(in, md);
             OutputStream out = Files.newOutputStream(target)) {

            byte[] buffer = new byte[BUFFER_SIZE];
            int read;
            while ((read = digestIn.read(buffer)) != -1) {   // 네트워크에서 8KB 받으면
                out.write(buffer, 0, read);                  // 바로 디스크에 쓰고, 통을 다시 쓴다
                total += read;
            }
        }

        return new StoredFile(total, HexFormat.of().formatHex(md.digest()));
    }

    private MessageDigest sha256() {
        try {
            return MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);   // SHA-256은 모든 JVM에 있으므로 여기 올 일은 없다
        }
    }
}
