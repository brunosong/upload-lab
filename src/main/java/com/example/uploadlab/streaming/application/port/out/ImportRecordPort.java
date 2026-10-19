package com.example.uploadlab.streaming.application.port.out;

import com.example.uploadlab.streaming.domain.CsvImport;

import java.util.List;

/**
 * 나가는 쪽 포트: CSV 넣기 기록(진행 상태, 결과)을 어디에 둘지.
 */
public interface ImportRecordPort {

    /** 같은 id가 있으면 덮어쓴다. 시작할 때 한 번, 끝날 때 한 번 부른다 */
    void save(CsvImport csvImport);

    /** 최신순 */
    List<CsvImport> findAll();
}
