package com.example.uploadlab.streaming.adapter.in.web;

import com.example.uploadlab.streaming.application.port.in.StreamUploadUseCase;
import com.example.uploadlab.streaming.domain.UploadedFile;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 들어오는 쪽 어댑터: HTTP 요청을 유스케이스 호출로 바꾼다.
 *
 * multipart가 아니라 application/octet-stream 으로 받는다.
 * 요청 본문(request.getInputStream())이 곧 파일 내용이라, 그대로 흘려 넘길 수 있다.
 */
@RestController
@RequestMapping("/api/streaming")
public class StreamUploadController {

    private final StreamUploadUseCase useCase;

    public StreamUploadController(StreamUploadUseCase useCase) {
        this.useCase = useCase;
    }

    @PutMapping(value = "/uploads", consumes = "application/octet-stream")
    public UploadResponse upload(@RequestHeader("X-File-Name") String encodedName,
                                 HttpServletRequest request) throws IOException {
        String originalName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);   // 한글 이름은 프론트에서 인코딩해서 보낸다

        UploadedFile file = useCase.upload(originalName, request.getInputStream());

        return new UploadResponse(file, usedHeapMb());
    }

    @GetMapping("/uploads")
    public List<UploadedFile> list() {
        return useCase.findAll();
    }

    /** 업로드 직후 서버 힙 사용량. 1GB를 올려도 이 값이 1GB만큼 늘지 않는 것을 확인하는 용도 */
    private long usedHeapMb() {
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
    }

    public record UploadResponse(UploadedFile file, long usedHeapMb) {
    }
}
