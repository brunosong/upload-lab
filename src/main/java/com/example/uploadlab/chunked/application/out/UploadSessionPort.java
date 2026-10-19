package com.example.uploadlab.chunked.application.out;

import com.example.uploadlab.chunked.domain.UploadSession;

import java.util.Optional;

public interface UploadSessionPort {
    void save(UploadSession session);
    Optional<UploadSession> findById(String uploadId);
}
