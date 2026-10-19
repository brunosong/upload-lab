package com.example.uploadlab.chunked.application.out;

import java.io.IOException;
import java.io.InputStream;

public interface ChunkStoragePort {
    /**
     * 조각을 임시 파일에 쓰고, 쓴 바이트 수를 돌려준다.
     * limit보다 많이 오면 limit + 1 바이트까지만 쓰고 멈춘다. 그러면 크기 검사에서 걸린다.
     */
    long writeTemp(String uploadId, int index, InputStream in, long limit) throws IOException;

    /** 임시 파일을 진짜 조각 파일로 바꾼다. 같은 번호가 이미 있으면 덮어쓴다 */
    void commit(String uploadId, int index) throws IOException;

    /** 임시 파일을 지운다. 전에 확정한 같은 번호 조각은 건드리지 않는다 */
    void discard(String uploadId, int index) throws IOException;
}
