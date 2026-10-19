package com.example.uploadlab.streaming.application;

import com.example.uploadlab.streaming.application.port.in.StreamReadUseCase;
import com.example.uploadlab.streaming.domain.CsvReadResult;
import com.example.uploadlab.streaming.domain.CsvRowReader;
import com.example.uploadlab.streaming.domain.TextReadResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 받으면서 읽는다. 읽은 줄은 로그로만 남기고 버린다.
 * 실제 서비스라면 로그를 찍는 자리에서 DB에 넣으면 된다.
 *
 * 줄마다 로그를 찍으면 1GB에 몇백만 줄이 찍힌다. 그래서
 *   - 처음 SHOW_FIRST 줄은 내용을 보여주고
 *   - 그다음은 PROGRESS_EVERY 줄마다 진행 상황만 찍는다
 */
@Service
public class StreamReadService implements StreamReadUseCase {

    private static final Logger log = LoggerFactory.getLogger(StreamReadService.class);

    private static final int SHOW_FIRST = 5;
    private static final int PROGRESS_EVERY = 100_000;
    private static final int MAX_CSV_ROW_CHARS = 1_000_000;

    @Override
    public TextReadResult readText(String originalName, InputStream body) throws IOException {
        Progress progress = new Progress("text", body);
        log.info("[text] 시작: {}", originalName);

        long lines = 0;
        // 주의: readLine()은 줄바꿈이 나올 때까지 모은다. 줄바꿈 없는 1GB가 오면 그대로 힙에 쌓인다
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(progress.in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines++;
                if (lines == 1 && line.startsWith("\uFEFF")) line = line.substring(1);   // BOM

                if (lines <= SHOW_FIRST) {
                    log.info("[text] {}번째 줄 (지금까지 받은 {} bytes): {}", lines, String.format("%,d", progress.in.count), line);
                } else if (lines % PROGRESS_EVERY == 0) {
                    progress.log(lines, "줄");
                }
            }
        }

        log.info("[text] 끝: {}줄, {}", String.format("%,d", lines), progress.summary());
        return new TextReadResult(originalName, lines, progress.in.count, progress.elapsed(), progress.maxHeapMb);
    }

    @Override
    public CsvReadResult readCsv(String originalName, InputStream body) throws IOException {
        Progress progress = new Progress("csv", body);
        log.info("[csv] 시작: {}", originalName);

        try (CsvRowReader reader = new CsvRowReader(
                new InputStreamReader(progress.in, StandardCharsets.UTF_8), MAX_CSV_ROW_CHARS)) {

            List<String> header = reader.next();
            if (header == null) {
                throw new IllegalArgumentException("빈 파일입니다");
            }
            log.info("[csv] 헤더 {}칸: {}", header.size(), header);

            long rows = 0;
            long badRows = 0;
            List<String> row;
            while ((row = reader.next()) != null) {
                if (row.size() == 1 && row.get(0).isEmpty()) continue;   // 빈 줄은 건너뛴다
                rows++;

                if (row.size() != header.size()) {
                    badRows++;
                    if (badRows <= SHOW_FIRST) {
                        log.warn("[csv] {}번째 행은 칸이 {}개입니다 (헤더는 {}개): {}", rows, row.size(), header.size(), row);
                    }
                } else if (rows <= SHOW_FIRST) {
                    log.info("[csv] {}번째 행 (지금까지 받은 {} bytes): {}", rows, String.format("%,d", progress.in.count), toMap(header, row));
                }
                if (rows % PROGRESS_EVERY == 0) {
                    progress.log(rows, "행");
                }
            }

            log.info("[csv] 끝: {}행, 칸 수가 틀린 행 {}개, {}", String.format("%,d", rows), badRows, progress.summary());
            return new CsvReadResult(originalName, header, rows, badRows,
                    progress.in.count, progress.elapsed(), progress.maxHeapMb);
        }
    }

    /** 로그에서 {이름=값, ...} 으로 보이게. 칸 안의 줄바꿈은 로그가 갈라지지 않게 \n 으로 보여준다 */
    private Map<String, String> toMap(List<String> header, List<String> row) {
        Map<String, String> map = new LinkedHashMap<>();
        for (int i = 0; i < header.size(); i++) {
            map.put(header.get(i), row.get(i).replace("\r", "").replace("\n", "\\n"));
        }
        return map;
    }

    private static String mb(long bytes) {
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    /** 몇 바이트 받았는지, 얼마나 걸렸는지, 힙을 얼마나 쓰는지 잰다 */
    private static class Progress {
        final String type;
        final CountingInputStream in;
        final long start = System.currentTimeMillis();
        long maxHeapMb;

        Progress(String type, InputStream body) {
            this.type = type;
            this.in = new CountingInputStream(body);
        }

        void log(long count, String unit) {
            log.info("[{}] {}{} 읽음, {}", type, String.format("%,d", count), unit, summary());
        }

        /** "받은 12.3 MB, 1.2초, 힙 30 MB" */
        String summary() {
            Runtime rt = Runtime.getRuntime();
            long heap = (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
            maxHeapMb = Math.max(maxHeapMb, heap);
            return "받은 " + mb(in.count) + ", " + String.format("%.1f", elapsed() / 1000.0) + "초, 힙 " + heap + " MB";
        }

        long elapsed() {
            return System.currentTimeMillis() - start;
        }
    }

    /** 네트워크에서 지금까지 받은 바이트 수를 센다 */
    private static class CountingInputStream extends FilterInputStream {
        long count;

        CountingInputStream(InputStream in) {
            super(in);
        }

        @Override
        public int read() throws IOException {
            int b = super.read();
            if (b != -1) count++;
            return b;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int n = super.read(b, off, len);
            if (n > 0) count += n;
            return n;
        }
    }
}
