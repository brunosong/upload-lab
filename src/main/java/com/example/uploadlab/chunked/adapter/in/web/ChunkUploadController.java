package com.example.uploadlab.chunked.adapter.in.web;

import com.example.uploadlab.chunked.application.in.ChunkUploadUseCase;
import com.example.uploadlab.chunked.domain.UploadSession;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.UUID;

@RestController
@RequestMapping("/api/chunked")
public class ChunkUploadController {

    private final ChunkUploadUseCase useCase;

    public ChunkUploadController(ChunkUploadUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping("/uploads")
    @ResponseStatus(HttpStatus.CREATED)
    public StartResponse start(@RequestBody StartRequest request) {
        UploadSession session = useCase.start(request.fileName, request.totalSize);
        return StartResponse.from(session);
    }

    @PutMapping(value = "/uploads/{uploadId}/chunks/{index}", consumes = "application/octet-stream")
    public ChunkResponse uploadChunk(@PathVariable String uploadId, @PathVariable int index,
                                     HttpServletRequest request) throws IOException {
        UploadSession session = useCase.receiveChunk(checkId(uploadId), index, request.getInputStream());
        return ChunkResponse.of(index, session);
    }

    private String checkId(String uploadId) {
        try {
            if (UUID.fromString(uploadId).toString().equals(uploadId)) {
                return uploadId;
            }
        } catch (IllegalArgumentException ignored) {

        }
        throw new IllegalArgumentException("잘못된 uploadId 입니다: " + uploadId);
    }

    public record StartRequest(String fileName, long totalSize) {}

    public record StartResponse(String uploadId, int chunkSize, int totalChunks) {
        public static StartResponse from(UploadSession session) {
            return new StartResponse(session.uploadId(), session.chunkSize(), session.totalChunks());
        }
    }

    public record ChunkResponse(int index, int receivedCount, int totalChunks) {
        static ChunkResponse of(int index, UploadSession session) {
            return new ChunkResponse(index, session.receivedCount(), session.totalChunks());
        }
    }
}
