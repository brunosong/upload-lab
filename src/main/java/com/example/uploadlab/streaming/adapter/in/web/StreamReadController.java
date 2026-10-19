package com.example.uploadlab.streaming.adapter.in.web;

import com.example.uploadlab.streaming.application.port.in.StreamReadUseCase;
import com.example.uploadlab.streaming.domain.CsvReadResult;
import com.example.uploadlab.streaming.domain.TextReadResult;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

/**
 * 받으면서 읽기. 파일을 저장하지 않고, 들어오는 대로 한 줄씩 읽고 버린다.
 * 무엇을 읽었는지는 서버 로그에 찍힌다.
 */
@RestController
@RequestMapping("/api/streaming/read")
public class StreamReadController {

    private final StreamReadUseCase useCase;

    public StreamReadController(StreamReadUseCase useCase) {
        this.useCase = useCase;
    }

    @PutMapping(value = "/text", consumes = "application/octet-stream")
    public TextReadResult readText(@RequestHeader("X-File-Name") String encodedName,
                                   HttpServletRequest request) throws IOException {
        return useCase.readText(decode(encodedName), request.getInputStream());
    }

    @PutMapping(value = "/csv", consumes = "application/octet-stream")
    public CsvReadResult readCsv(@RequestHeader("X-File-Name") String encodedName,
                                 HttpServletRequest request) throws IOException {
        return useCase.readCsv(decode(encodedName), request.getInputStream());
    }

    private String decode(String encodedName) {
        return URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
    }
}
