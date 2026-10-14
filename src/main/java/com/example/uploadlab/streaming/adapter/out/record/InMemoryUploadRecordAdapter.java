package com.example.uploadlab.streaming.adapter.out.record;

import com.example.uploadlab.streaming.application.port.out.UploadRecordPort;
import com.example.uploadlab.streaming.domain.UploadedFile;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 업로드 기록을 메모리에 둔다. 서버를 끄면 사라진다.
 *
 * lab.record.type=memory 이거나, 설정이 아예 없을 때(matchIfMissing) 빈으로 등록된다.
 * 다른 값(file)이면 이 클래스는 빈으로 안 올라가고 FileUploadRecordAdapter 가 대신 올라간다.
 */
@Component
@ConditionalOnProperty(name = "lab.record.type", havingValue = "memory", matchIfMissing = true)
public class InMemoryUploadRecordAdapter implements UploadRecordPort {

    private static final Logger log = LoggerFactory.getLogger(InMemoryUploadRecordAdapter.class);

    private final List<UploadedFile> files = new CopyOnWriteArrayList<>();

    public InMemoryUploadRecordAdapter() {
        log.info("업로드 기록 어댑터: 메모리");
    }

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
