package com.example.uploadlab.streaming.application;

import com.example.uploadlab.streaming.adapter.out.db.JdbcCdnFileRowAdapter;
import com.example.uploadlab.streaming.adapter.out.db.JdbcImportRecordAdapter;
import com.example.uploadlab.streaming.domain.CsvImport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.JdbcTest;
import org.springframework.context.annotation.Import;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.SequenceInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 진짜 DB(H2 메모리)에 진짜 JDBC 어댑터를 끼워서 돌린다.
 * @JdbcTest 는 DataSource를 메모리 H2로 바꾸고 schema.sql 을 실행한다. 웹 쪽 빈은 안 올린다.
 */
@JdbcTest
@Import({CsvImportService.class, JdbcImportRecordAdapter.class, JdbcCdnFileRowAdapter.class})
class CsvImportServiceTest {

    @Autowired
    CsvImportService service;

    @Autowired
    JdbcCdnFileRowAdapter files;

    @Test
    void 배치_크기를_넘는_행을_모두_넣고_틀린_행은_건너뛴다() throws Exception {
        StringBuilder csv = new StringBuilder("path,size,memo\n");
        for (int i = 0; i < 2500; i++) {          // 1000 + 1000 + 남은 500
            csv.append("/img/").append(i).append(".jpg,").append(i).append(",메모\n");
        }
        csv.append("/bad.jpg,크기,메모\n");          // size가 숫자가 아님
        csv.append("/short.jpg,1\n");                // 칸이 모자람

        CsvImport result = service.importCsv("list.csv", stream(csv.toString()));

        assertThat(result.status()).isEqualTo(CsvImport.Status.COMPLETED);
        assertThat(result.savedRows()).isEqualTo(2500);
        assertThat(result.badRows()).isEqualTo(2);
        assertThat(files.countByImportId(result.id())).isEqualTo(2500);
        assertThat(service.findAll()).containsExactly(result);
    }

    @Test
    void 중간에_업로드가_끊기면_넣었던_행을_지우고_FAILED로_남긴다() {
        StringBuilder csv = new StringBuilder("path,size\n");
        for (int i = 0; i < 1500; i++) {          // 1000행은 이미 DB에 들어간 뒤에 끊긴다
            csv.append("/img/").append(i).append(".jpg,").append(i).append("\n");
        }
        InputStream broken = new SequenceInputStream(stream(csv.toString()), new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException("연결이 끊겼습니다");
            }
        });

        assertThatThrownBy(() -> service.importCsv("broken.csv", broken)).isInstanceOf(IOException.class);

        CsvImport failed = service.findAll().get(0);
        assertThat(failed.status()).isEqualTo(CsvImport.Status.FAILED);
        assertThat(failed.error()).isEqualTo("연결이 끊겼습니다");
        assertThat(files.countByImportId(failed.id())).isZero();
    }

    @Test
    void 헤더가_틀리면_FAILED로_남기고_예외를_던진다() {
        assertThatThrownBy(() -> service.importCsv("no-size.csv", stream("path,bytes\n/a.jpg,1\n")))
                .isInstanceOf(IllegalArgumentException.class);

        CsvImport failed = service.findAll().get(0);
        assertThat(failed.status()).isEqualTo(CsvImport.Status.FAILED);
        assertThat(failed.error()).contains("path, size");
    }

    private InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
