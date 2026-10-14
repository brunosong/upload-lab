package com.example.uploadlab.streaming;

import com.example.uploadlab.streaming.adapter.out.record.FileUploadRecordAdapter;
import com.example.uploadlab.streaming.adapter.out.record.InMemoryUploadRecordAdapter;
import com.example.uploadlab.streaming.adapter.out.storage.LocalDiskStorageAdapter;
import com.example.uploadlab.streaming.application.StreamUploadService;
import com.example.uploadlab.streaming.application.port.out.UploadRecordPort;
import com.example.uploadlab.streaming.domain.UploadedFile;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.ByteArrayInputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 같은 포트(UploadRecordPort)에 어댑터를 바꿔 끼우는 두 가지 방법.
 */
class RecordAdapterSwapTest {

    /** 방법 1: 스프링 없이 손으로 끼운다. 서비스 생성자에 넣는 객체만 바꾸면 된다 */
    @Test
    void 손으로_끼우기_파일_어댑터는_새로_만들어도_기록이_남는다(@TempDir Path dir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());

        StreamUploadService first = new StreamUploadService(
                new LocalDiskStorageAdapter(dir.toString()),
                new FileUploadRecordAdapter(dir.toString(), mapper));     // 여기만 바꿨다
        UploadedFile saved = first.upload("a.tsv", new ByteArrayInputStream("hello".getBytes()));

        // 서버를 껐다 켠 것처럼 어댑터를 새로 만든다
        StreamUploadService restarted = new StreamUploadService(
                new LocalDiskStorageAdapter(dir.toString()),
                new FileUploadRecordAdapter(dir.toString(), mapper));

        assertThat(restarted.findAll()).containsExactly(saved);
    }

    @Test
    void 손으로_끼우기_메모리_어댑터는_새로_만들면_기록이_사라진다(@TempDir Path dir) throws Exception {
        StreamUploadService first = new StreamUploadService(
                new LocalDiskStorageAdapter(dir.toString()),
                new InMemoryUploadRecordAdapter());
        first.upload("a.tsv", new ByteArrayInputStream("hello".getBytes()));

        StreamUploadService restarted = new StreamUploadService(
                new LocalDiskStorageAdapter(dir.toString()),
                new InMemoryUploadRecordAdapter());

        assertThat(restarted.findAll()).isEmpty();
    }

    /** 방법 2: 스프링이 설정값을 보고 고른다. 코드는 그대로, 설정만 다르다 */
    @Nested
    @SpringBootTest(properties = {"lab.record.type=memory", "lab.upload.dir=target/test-uploads"})
    class 설정이_memory면 {
        @Autowired
        UploadRecordPort port;

        @Test
        void 메모리_어댑터가_끼워진다() {
            assertThat(port).isInstanceOf(InMemoryUploadRecordAdapter.class);
        }
    }

    @Nested
    @SpringBootTest(properties = {"lab.record.type=file", "lab.upload.dir=target/test-uploads"})
    class 설정이_file이면 {
        @Autowired
        UploadRecordPort port;

        @Test
        void 파일_어댑터가_끼워진다() {
            assertThat(port).isInstanceOf(FileUploadRecordAdapter.class);
        }
    }
}
