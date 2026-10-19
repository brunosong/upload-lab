package com.example.uploadlab.chunked.application;

import com.example.uploadlab.chunked.adapter.out.session.InMemoryUploadSessionAdapter;
import com.example.uploadlab.chunked.adapter.out.storage.LocalDiskChunkStorageAdapter;
import com.example.uploadlab.chunked.domain.CompletedUpload;
import com.example.uploadlab.chunked.domain.UploadSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
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

    @Test
    void 조각을_받으면_파일로_저장되고_받은_개수가_는다() throws Exception {
        UploadSession session = service.start("a.bin", 10);// 4 + 4 + 2
        String id = session.uploadId();

        service.receiveChunk(id, 0, body(1,2,3,4));

        UploadSession after = service.receiveChunk(id, 2, body(9, 9));

        assertThat(Files.readAllBytes(part(id, 0))).containsExactly(1, 2, 3, 4);
        assertThat(Files.readAllBytes(part(id, 2))).containsExactly(9, 9);
        assertThat(Files.exists(temp(id, 0))).isFalse();
        assertThat(after.receivedCount()).isEqualTo(2);
        assertThat(after.missingChunks()).containsExactly(1);

    }

    @Test
    void 같은_번호를_다시_보내면_덮어쓰고_한_번으로_센다() throws Exception {
        String id = service.start("a.bin", 10).uploadId();

        service.receiveChunk(id, 0, body(1, 1, 1, 1));
        UploadSession after = service.receiveChunk(id, 0, body(2, 2, 2, 2));

        assertThat(Files.readAllBytes(part(id, 0))).containsExactly(2, 2, 2, 2);
        assertThat(after.receivedCount()).isEqualTo(1);
    }

    @Test
    void 크기가_틀리면_거부하고_파일도_남기지_않는다() throws Exception {
        String id = service.start("a.bin", 10).uploadId();

        assertThatThrownBy(() -> service.receiveChunk(id, 0, body(1, 2, 3)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.exists(part(id, 0))).isFalse();
        assertThat(Files.exists(temp(id, 0))).isFalse();
        assertThat(sessions.findById(id).orElseThrow().receivedCount()).isZero();
    }

    @Test
    void 틀린_조각이_와도_전에_받은_같은_번호_조각은_남는다() throws Exception {
        String id = service.start("a.bin", 10).uploadId();
        service.receiveChunk(id, 0, body(1, 2, 3, 4));

        assertThatThrownBy(() -> service.receiveChunk(id, 0, body(7)))
                .isInstanceOf(IllegalArgumentException.class);

        assertThat(Files.readAllBytes(part(id, 0))).containsExactly(1, 2, 3, 4);
        assertThat(sessions.findById(id).orElseThrow().receivedCount()).isEqualTo(1);
    }

    @Test
    void 조각보다_큰_본문은_끝까지_읽지_않는다() throws Exception {
        String id = service.start("a.bin", 10).uploadId();
        ByteArrayInputStream huge = new ByteArrayInputStream(new byte[1000]);

        assertThatThrownBy(() -> service.receiveChunk(id, 0, huge))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("받음: 5");                // 4 + 1 바이트만 읽고 멈췄다

        assertThat(huge.available()).isEqualTo(1000 - 5);
    }

    @Test
    void 모르는_uploadId면_세션_없음() {
        String unknown = UUID.randomUUID().toString();

        assertThatThrownBy(() -> service.receiveChunk(unknown, 0, body(1, 2, 3, 4)))
                .isInstanceOf(UploadSessionNotFoundException.class);
    }

    // ---- complete ----

    @Test
    void 완료하면_조각을_번호_순서대로_합치고_조각_폴더를_지운다() throws Exception {
        String id = service.start("a.bin", 10).uploadId();
        service.receiveChunk(id, 2, body(9, 10));          // 일부러 순서를 섞어서 보낸다
        service.receiveChunk(id, 0, body(1, 2, 3, 4));
        service.receiveChunk(id, 1, body(5, 6, 7, 8));

        CompletedUpload done = service.complete(id);

        byte[] expected = {1, 2, 3, 4, 5, 6, 7, 8, 9, 10};
        assertThat(Files.readAllBytes(tempDir.resolve("chunked-" + id + ".dat"))).isEqualTo(expected);
        assertThat(done.size()).isEqualTo(10);
        assertThat(done.totalChunks()).isEqualTo(3);
        assertThat(done.originalName()).isEqualTo("a.bin");
        assertThat(done.sha256()).isEqualTo(sha256(expected));
        assertThat(Files.exists(tempDir.resolve("chunked").resolve(id))).isFalse();
        assertThat(sessions.findById(id).orElseThrow().status()).isEqualTo(UploadSession.Status.COMPLETED);
    }

    @Test
    void 조각이_빠졌으면_완료하지_않고_조각도_남겨_둔다() throws Exception {
        String id = service.start("a.bin", 10).uploadId();
        service.receiveChunk(id, 0, body(1, 2, 3, 4));

        assertThatThrownBy(() -> service.complete(id))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("[1, 2]");

        assertThat(Files.exists(part(id, 0))).isTrue();     // 빠진 조각만 보내고 다시 완료하면 된다
        assertThat(Files.exists(tempDir.resolve("chunked-" + id + ".dat"))).isFalse();
    }

    @Test
    void 두_번_완료할_수_없다() throws Exception {
        String id = service.start("a.bin", 2).uploadId();
        service.receiveChunk(id, 0, body(1, 2));
        service.complete(id);

        assertThatThrownBy(() -> service.complete(id)).isInstanceOf(IllegalStateException.class);
    }

    private String sha256(byte[] data) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
    }


    private ByteArrayInputStream body(int... values) {
        byte[] bytes = new byte[values.length];
        for (int i = 0; i < values.length; i++) bytes[i] = (byte) values[i];
        return new ByteArrayInputStream(bytes);
    }

    private Path part(String id, int index) {
        return tempDir.resolve("chunked").resolve(id).resolve(index + ".part");
    }

    private Path temp(String id, int index) {
        return tempDir.resolve("chunked").resolve(id).resolve(index + ".part.tmp");
    }

}