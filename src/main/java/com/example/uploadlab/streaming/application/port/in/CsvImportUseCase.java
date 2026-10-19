package com.example.uploadlab.streaming.application.port.in;

import com.example.uploadlab.streaming.domain.CsvImport;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * 들어오는 쪽 포트: CSV를 받으면서 DB에 넣는다.
 */
public interface CsvImportUseCase {

    /** 다 넣으면 COMPLETED. 중간에 실패하면 그 CSV에서 넣은 행을 지우고 FAILED로 남긴 뒤 예외를 다시 던진다 */
    CsvImport importCsv(String originalName, InputStream body) throws IOException;

    /** 지금까지 넣은 기록. 최신순 */
    List<CsvImport> findAll();
}
