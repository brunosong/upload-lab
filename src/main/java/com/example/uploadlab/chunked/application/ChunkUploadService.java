package com.example.uploadlab.chunked.application;

import com.example.uploadlab.chunked.application.in.ChunkUploadUseCase;
import com.example.uploadlab.chunked.application.out.ChunkStoragePort;
import com.example.uploadlab.chunked.application.out.ChunkStoragePort.AssembledFile;
import com.example.uploadlab.chunked.application.out.UploadSessionPort;
import com.example.uploadlab.chunked.domain.CompletedUpload;
import com.example.uploadlab.chunked.domain.UploadSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.UUID;

@Service
public class ChunkUploadService implements ChunkUploadUseCase {

    private final UploadSessionPort sessions;
    private final ChunkStoragePort storage;
    private final int chunkSize;    // 조각 크기는 클라이언트가 아니라 서버 설정이 정한다.

    public ChunkUploadService(UploadSessionPort sessions,
                              ChunkStoragePort storage,
                              @Value("${lab.chunked.chunk-size}") int chunkSize) {
        this.sessions = sessions;
        this.chunkSize = chunkSize;
        this.storage = storage;
    }

    @Override
    public UploadSession start(String fileName, long totalSize) {

        String uploadId = UUID.randomUUID().toString();

        UploadSession session = UploadSession.start(uploadId, fileName, totalSize, chunkSize, LocalDateTime.now());
        sessions.save(session);

        return session;
    }

    @Override
    public UploadSession receiveChunk(String uploadId, int index, InputStream body) throws IOException {
        UploadSession session = findSession(uploadId);

        long expected = session.expectedSizeOf(index); // 번호가 틀리면 디스크에 쓰기 전에 여기서 막힌다.

        long size = storage.writeTemp(uploadId, index, body, expected);

        try {
            session.checkCanReceive(index, size);  // 크기가 틀리거나 이미 끝난 업로드면 예외
        } catch (RuntimeException e) {
            storage.discard(uploadId, index);       // 틀린 조각만 버린다. 전에 받은 같은 번호 조각은 남는다.
            throw e;
        }

        storage.commit(uploadId, index);         // 파일이 확정된 다음에
        session.markReceived(index, size);      // 받았다고 기록한다.
        sessions.save(session);                           // 메모리에서는 없어도 되지만, DB로

        return session;
    }

    @Override
    public CompletedUpload complete(String uploadId) throws IOException {
        UploadSession session = findSession(uploadId);

        session.checkCanComplete();             // 빠진 조각이 있거나 이미 끝났으면 합치기 전에 막힌다

        AssembledFile file = storage.assemble(uploadId, session.totalChunks());   // 0.part, 1.part … 순서대로 붙인다
        if (file.size() != session.totalSize()) {
            throw new IllegalStateException("합친 크기가 다릅니다. 기대: " + session.totalSize() + ", 합침: " + file.size());
        }

        session.complete();                     // 파일이 만들어진 다음에 완료로 바꾼다
        sessions.save(session);
        storage.deleteChunks(uploadId);         // 다 합쳤으니 조각 폴더는 지운다

        LocalDateTime now = LocalDateTime.now();
        return new CompletedUpload(
                uploadId,
                session.originalName(),
                file.size(),
                session.totalChunks(),
                file.sha256(),
                Duration.between(session.createdAt(), now).toMillis(),
                now);
    }

    private UploadSession findSession(String uploadId) {
        return sessions.findById(uploadId)
                .orElseThrow(() -> new UploadSessionNotFoundException(uploadId));
    }
}
