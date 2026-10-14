package com.example.uploadlab.streaming.adapter.out.record;

import com.example.uploadlab.streaming.application.port.out.UploadRecordPort;
import com.example.uploadlab.streaming.domain.UploadedFile;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 업로드 기록을 메모리에 둔다. 서버를 끄면 사라진다.
 * 나중에 MongoDB 어댑터를 만들어 이 클래스와 바꿔 끼운다.
 */
@Component
public class InMemoryUploadRecordAdapter implements UploadRecordPort {

    private final List<UploadedFile> files = new CopyOnWriteArrayList<>();

    @Override
    public void save(UploadedFile file) {
        files.add(file);
    }

    @Override
    public List<UploadedFile> findAll() {
        List<UploadedFile> newestFirst = new ArrayList<>(files);
        newestFirst.sort((a, b) -> b.uploadedAt().compareTo(a.uploadedAt()));
        return newestFirst;
    }
}
