package com.example.uploadlab.streaming.application;

import com.example.uploadlab.streaming.domain.CsvReadResult;
import com.example.uploadlab.streaming.domain.TextReadResult;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StreamReadServiceTest {

    private final StreamReadService service = new StreamReadService();

    @Test
    void 텍스트_줄_수와_바이트_수를_센다() throws Exception {
        byte[] text = "첫 줄\n둘째 줄\r\n셋째 줄".getBytes(StandardCharsets.UTF_8);

        TextReadResult result = service.readText("a.txt", new ByteArrayInputStream(text));

        assertThat(result.lines()).isEqualTo(3);
        assertThat(result.bytes()).isEqualTo(text.length);
    }

    @Test
    void CSV_헤더와_행_수와_칸_수가_틀린_행을_센다() throws Exception {
        String csv = "path,size\n/a.jpg,100\n\n/b.jpg,200,남는칸\n\"/c, d.jpg\",300\n";

        CsvReadResult result = service.readCsv("a.csv", stream(csv));

        assertThat(result.header()).containsExactly("path", "size");
        assertThat(result.rows()).isEqualTo(3);     // 빈 줄은 세지 않는다
        assertThat(result.badRows()).isEqualTo(1);
    }

    @Test
    void 빈_CSV는_거절한다() {
        assertThatThrownBy(() -> service.readCsv("empty.csv", stream("")))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void 큰_CSV를_통째로_올리지_않고_읽는다() throws Exception {
        long rowCount = 1_000_000;   // 약 25MB. 배열로 만들지 않고 행을 그때그때 만들어 흘려 보낸다
        InputStream generated = new InputStream() {
            long next = -1;          // -1은 헤더
            byte[] current = new byte[0];
            int pos;

            @Override
            public int read() {
                if (pos == current.length) {
                    if (next == rowCount) return -1;
                    String line = next < 0 ? "path,size\n" : "/img/" + next + ".jpg," + next + "\n";
                    current = line.getBytes(StandardCharsets.UTF_8);
                    pos = 0;
                    next++;
                }
                return current[pos++];
            }
        };

        CsvReadResult result = service.readCsv("big.csv", generated);

        assertThat(result.header()).isEqualTo(List.of("path", "size"));
        assertThat(result.rows()).isEqualTo(rowCount);
        assertThat(result.badRows()).isZero();
    }

    private InputStream stream(String s) {
        return new ByteArrayInputStream(s.getBytes(StandardCharsets.UTF_8));
    }
}
