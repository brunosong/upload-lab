package com.example.uploadlab.streaming.application.port.out;

import java.io.IOException;
import java.io.InputStream;

/**
 * 나가는 쪽 포트: 데이터를 어디에 저장할지는 어댑터가 정한다.
 * 지금은 로컬 디스크지만, 나중에 오브젝트 스토리지로 바꿔도 서비스 코드는 그대로다.
 */
public interface FileStoragePort {

    StoredFile save(String id, InputStream in) throws IOException;

    /** 저장 결과: 몇 바이트를 썼고 해시가 무엇인지 */
    record StoredFile(long size, String sha256) {
    }
}
