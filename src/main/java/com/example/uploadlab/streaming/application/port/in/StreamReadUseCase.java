package com.example.uploadlab.streaming.application.port.in;

import com.example.uploadlab.streaming.domain.CsvReadResult;
import com.example.uploadlab.streaming.domain.TextReadResult;

import java.io.IOException;
import java.io.InputStream;

/**
 * 들어오는 쪽 포트: 받으면서 읽기. 저장하지 않는다.
 */
public interface StreamReadUseCase {

    /** 한 줄씩 읽는다 */
    TextReadResult readText(String originalName, InputStream body) throws IOException;

    /** CSV를 한 행씩 읽는다. 첫 행은 헤더다 */
    CsvReadResult readCsv(String originalName, InputStream body) throws IOException;
}
