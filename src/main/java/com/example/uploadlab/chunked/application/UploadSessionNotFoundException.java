package com.example.uploadlab.chunked.application;

public class UploadSessionNotFoundException extends RuntimeException {

    public UploadSessionNotFoundException(String uploadId) {
        super("업로드 세션이 없습니다: " + uploadId);
    }
}
