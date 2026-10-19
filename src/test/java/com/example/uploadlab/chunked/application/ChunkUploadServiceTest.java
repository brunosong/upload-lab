package com.example.uploadlab.chunked.application;

import com.example.uploadlab.chunked.adapter.out.session.InMemoryUploadSessionAdapter;
import com.example.uploadlab.chunked.adapter.out.storage.LocalDiskChunkStorageAdapter;
import com.example.uploadlab.chunked.domain.UploadSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 스프링 없이 포트에 어댑터를 직접 끼워서 돌린다.
 */
class ChunkUploadServiceTest {

    @TempDir
    Path tempDir;

    InMemoryUploadSessionAdapter sessions;
    ChunkUploadService service;

    @BeforeEach
    void setUp() {
        sessions = new InMemoryUploadSessionAdapter();
        service = new ChunkUploadService(sessions, new LocalDiskChunkStorageAdapter(tempDir.toString()), 4);
    }



    @Test
    void 시작하면_서버가_정한_조각_크기로_세션을_만들고_저장한다() {

        UploadSession session = service.start("목록.tsv", 10);

        assertThat(UUID.fromString(session.uploadId()).toString()).isEqualTo(session.uploadId());
        assertThat(session.originalName()).isEqualTo("목록.tsv");
        assertThat(session.chunkSize()).isEqualTo(4);
        assertThat(session.totalChunks()).isEqualTo(3);
        assertThat(session.status()).isEqualTo(UploadSession.Status.UPLOADING);
    }

    @Test
    void 시작할_때마다_다른_uploadId를_준다() {
        assertThat(service.start("a",10)).isNotEqualTo(service.start("a", 10));
    }

    @Test
    void 잘못된_요청이면_시작할_수_없다() {
        assertThatThrownBy(() -> service.start("a", 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> service.start(" ", 10)).isInstanceOf(IllegalArgumentException.class);
    }

}