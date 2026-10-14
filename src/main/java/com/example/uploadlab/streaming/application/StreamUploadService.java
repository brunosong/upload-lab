package com.example.uploadlab.streaming.application;

import com.example.uploadlab.streaming.application.port.in.StreamUploadUseCase;
import com.example.uploadlab.streaming.application.port.out.FileStoragePort;
import com.example.uploadlab.streaming.application.port.out.FileStoragePort.StoredFile;
import com.example.uploadlab.streaming.application.port.out.UploadRecordPort;
import com.example.uploadlab.streaming.domain.UploadedFile;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * 스트리밍 업로드의 흐름만 담는다.
 * 어떻게 저장하는지(디스크), 어디에 기록하는지(메모리)는 포트 너머에 있다.
 */
@Service
public class StreamUploadService implements StreamUploadUseCase {

    private final FileStoragePort storage;
    private final UploadRecordPort records;

    public StreamUploadService(FileStoragePort storage, UploadRecordPort records) {
        this.storage = storage;
        this.records = records;
    }

    @Override
    public UploadedFile upload(String originalName, InputStream body) throws IOException {
        String id = UUID.randomUUID().toString();
        long start = System.currentTimeMillis();

        StoredFile stored = storage.save(id, body);

        UploadedFile file = new UploadedFile(
                id,
                originalName,
                stored.size(),
                stored.sha256(),
                System.currentTimeMillis() - start,
                LocalDateTime.now());
        records.save(file);
        return file;
    }

    @Override
    public List<UploadedFile> findAll() {
        return records.findAll();
    }
}
