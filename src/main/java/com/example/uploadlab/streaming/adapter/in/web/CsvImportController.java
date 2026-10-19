package com.example.uploadlab.streaming.adapter.in.web;

import com.example.uploadlab.streaming.application.port.in.CsvImportUseCase;
import com.example.uploadlab.streaming.domain.CsvImport;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * CSV를 받으면서 DB에 넣는다. 파일은 디스크에 저장하지 않는다.
 * CSV에는 path, size 칸이 있어야 한다 (순서는 상관없고 다른 칸은 무시).
 */
@RestController
@RequestMapping("/api/streaming/imports")
public class CsvImportController {

    private final CsvImportUseCase useCase;

    public CsvImportController(CsvImportUseCase useCase) {
        this.useCase = useCase;
    }

    @PostMapping(consumes = "application/octet-stream")
    @ResponseStatus(HttpStatus.CREATED)
    public CsvImport importCsv(@RequestHeader("X-File-Name") String encodedName,
                               HttpServletRequest request) throws IOException {
        String originalName = URLDecoder.decode(encodedName, StandardCharsets.UTF_8);
        return useCase.importCsv(originalName, request.getInputStream());
    }

    @GetMapping
    public List<CsvImport> list() {
        return useCase.findAll();
    }
}
