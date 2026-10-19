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

    /** 0.part, 1.part … 를 번호 순서대로 이어 붙여 파일 하나로 만든다. 붙이면서 SHA-256을 계산한다 */
    AssembledFile assemble(String uploadId, int totalChunks) throws IOException;

    /** 조각 폴더를 통째로 지운다 */
    void deleteChunks(String uploadId) throws IOException;

    /** 합친 파일의 크기와 해시 */
    record AssembledFile(long size, String sha256) {
    }
}
