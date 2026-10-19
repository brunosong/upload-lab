package com.example.uploadlab.chunked.adapter.in.web;

import com.example.uploadlab.chunked.application.UploadSessionNotFoundException;
import com.example.uploadlab.chunked.application.in.ChunkUploadUseCase;
import com.example.uploadlab.chunked.domain.CompletedUpload;
import com.example.uploadlab.chunked.domain.UploadSession;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 서비스 없이 컨트롤러만 띄운다. 유스케이스는 가짜(mock)로 끼운다.
 * 요청이 유스케이스 호출로 바뀌는지, 예외가 맞는 상태 코드로 바뀌는지만 본다.
 */
@WebMvcTest(ChunkUploadController.class)
class ChunkUploadControllerTest {

    static final String ID = "3f2b8c1e-9d4a-4b6f-8e2a-1c5d7f9b0a12";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    ChunkUploadUseCase useCase;

    @Test
    void 시작하면_201과_uploadId를_준다() throws Exception {
        when(useCase.start("목록.tsv", 10))
                .thenReturn(UploadSession.start(ID, "목록.tsv", 10, 4, LocalDateTime.now()));

        mvc.perform(post("/api/chunked/uploads")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fileName\":\"목록.tsv\",\"totalSize\":10}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.uploadId").value(ID))
                .andExpect(jsonPath("$.chunkSize").value(4))
                .andExpect(jsonPath("$.totalChunks").value(3));
    }

    @Test
    void 완료하면_합친_파일_정보를_준다() throws Exception {
        when(useCase.complete(ID))
                .thenReturn(new CompletedUpload(ID, "목록.tsv", 10, 3, "abc", 5, LocalDateTime.now()));

        mvc.perform(post("/api/chunked/uploads/{id}/complete", ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(10))
                .andExpect(jsonPath("$.sha256").value("abc"));
    }

    @Test
    void 경로처럼_생긴_uploadId는_400이고_서비스까지_가지_않는다() throws Exception {
        mvc.perform(post("/api/chunked/uploads/{id}/complete", "..%2F..%2Fx"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/chunked/uploads/{id}/complete", "1-1-1-1-1"))
                .andExpect(status().isBadRequest());

        verify(useCase, never()).complete(anyString());
    }

    @Test
    void 조각_크기가_틀리면_400과_이유를_준다() throws Exception {
        when(useCase.receiveChunk(eq(ID), anyInt(), any()))
                .thenThrow(new IllegalArgumentException("1번 조각 크기가 다릅니다"));

        mvc.perform(put("/api/chunked/uploads/{id}/chunks/{index}", ID, 1)
                        .contentType(MediaType.APPLICATION_OCTET_STREAM)
                        .content(new byte[]{1}))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("1번 조각 크기가 다릅니다"));
    }

    @Test
    void 조각이_빠졌는데_완료하면_409() throws Exception {
        when(useCase.complete(ID)).thenThrow(new IllegalStateException("아직 안 온 조각이 있습니다: [2]"));

        mvc.perform(post("/api/chunked/uploads/{id}/complete", ID))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value("아직 안 온 조각이 있습니다: [2]"));
    }

    @Test
    void 모르는_세션이면_404() throws Exception {
        when(useCase.complete(ID)).thenThrow(new UploadSessionNotFoundException(ID));

        mvc.perform(post("/api/chunked/uploads/{id}/complete", ID))
                .andExpect(status().isNotFound());
    }
}
