package com.example.uploadlab.streaming.application.port.in;

import com.example.uploadlab.streaming.domain.UploadedFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 들어오는 쪽 포트: 바깥(웹 컨트롤러)이 이 기능에 시키는 일.
 */
public interface StreamUploadUseCase {

    /** 흘러 들어오는 데이터를 그대로 저장한다. 전체를 메모리에 올리지 않는다. */
    UploadedFile upload(String originalName, InputStream body) throws IOException;

    /** 지금까지 올린 파일 목록 */
    List<UploadedFile> findAll();
}
