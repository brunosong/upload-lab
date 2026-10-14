package com.example.uploadlab.streaming.adapter.out.record;

import com.example.uploadlab.streaming.application.port.out.UploadRecordPort;
import com.example.uploadlab.streaming.domain.UploadedFile;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;

/**
 * 업로드 기록을 파일(records.jsonl)에 한 줄씩 남긴다. 서버를 껐다 켜도 남는다.
 *
 * InMemoryUploadRecordAdapter 와 같은 포트(UploadRecordPort)를 구현한다.
 * 서비스는 둘 중 누가 끼워졌는지 모른다.
 *
 * lab.record.type=file 일 때만 빈으로 등록된다.
 */
@Component
@ConditionalOnProperty(name = "lab.record.type", havingValue = "file")
public class FileUploadRecordAdapter implements UploadRecordPort {

    private static final Logger log = LoggerFactory.getLogger(FileUploadRecordAdapter.class);

    private final Path recordFile;
    private final ObjectMapper objectMapper;

    public FileUploadRecordAdapter(@Value("${lab.upload.dir}") String baseDir, ObjectMapper objectMapper) {
        this.recordFile = Path.of(baseDir).resolve("records.jsonl");
        this.objectMapper = objectMapper;
        log.info("업로드 기록 어댑터: 파일 ({})", recordFile.toAbsolutePath());
    }

    @Override
    public synchronized void save(UploadedFile file) {
        try {
            Files.createDirectories(recordFile.getParent());
            String line = objectMapper.writeValueAsString(file) + System.lineSeparator();
            Files.writeString(recordFile, line, StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public synchronized List<UploadedFile> findAll() {
        if (Files.notExists(recordFile)) {
            return List.of();
        }
        try {
            List<UploadedFile> files = new ArrayList<>();
            for (String line : Files.readAllLines(recordFile)) {   // 기록 파일은 작아서 통째로 읽어도 된다
                if (!line.isBlank()) {
                    files.add(objectMapper.readValue(line, UploadedFile.class));
                }
            }
            files.sort((a, b) -> b.uploadedAt().compareTo(a.uploadedAt()));
            return files;
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("기록 파일 형식이 깨졌습니다: " + recordFile, e);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
