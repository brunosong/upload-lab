package com.example.uploadlab.streaming.application.port.out;

import com.example.uploadlab.streaming.domain.UploadedFile;

import java.util.List;

/**
 * 나가는 쪽 포트: 업로드 기록을 어디에 남길지는 어댑터가 정한다.
 * 지금은 메모리에 두고, 나중에 MongoDB 어댑터로 바꾼다.
 */
public interface UploadRecordPort {

    void save(UploadedFile file);

    List<UploadedFile> findAll();
}
