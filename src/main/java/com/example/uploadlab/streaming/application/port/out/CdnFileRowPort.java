package com.example.uploadlab.streaming.application.port.out;

import com.example.uploadlab.streaming.domain.CdnFileRow;

import java.util.List;

/**
 * 나가는 쪽 포트: CSV에서 읽은 행을 어디에 넣을지.
 */
public interface CdnFileRowPort {

    /** 한 번에 여러 행을 넣는다. 어느 CSV에서 왔는지 importId로 묶는다 */
    void saveAll(String importId, List<CdnFileRow> rows);

    /** 그 CSV에서 넣은 행을 모두 지운다. 지운 행 수를 돌려준다 */
    long deleteByImportId(String importId);

    long countByImportId(String importId);
}
