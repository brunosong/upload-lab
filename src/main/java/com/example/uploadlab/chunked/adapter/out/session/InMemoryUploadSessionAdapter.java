package com.example.uploadlab.chunked.adapter.out.session;

import com.example.uploadlab.chunked.application.out.UploadSessionPort;
import com.example.uploadlab.chunked.domain.UploadSession;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 업로드 세션을 메모리에 둔다. 서버를 끄면 진행 중이던 업로드는 사라진다.
 */
@Component
public class InMemoryUploadSessionAdapter implements UploadSessionPort {

    private final Map<String, UploadSession> sessions = new ConcurrentHashMap<>();   // uploadId → 세션

    @Override
    public void save(UploadSession session) {
        sessions.put(session.uploadId(), session);
    }

    @Override
    public Optional<UploadSession> findById(String uploadId) {
        return Optional.ofNullable(sessions.get(uploadId));
    }
}
