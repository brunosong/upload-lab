package com.example.uploadlab.chunked.application.in;

import com.example.uploadlab.chunked.domain.CompletedUpload;
import com.example.uploadlab.chunked.domain.UploadSession;

import java.io.IOException;
import java.io.InputStream;

public interface ChunkUploadUseCase {

    /** 업로드 세션을 연다. 조각 크기와 개수는 서버가 정한다 */
    UploadSession start(String fileName, long totalSize);

    UploadSession receiveChunk(String uploadId, int index, InputStream body) throws IOException;

    /** 조각을 번호 순서대로 합쳐 파일 하나로 만든다 */
    CompletedUpload complete(String uploadId) throws IOException;
}
