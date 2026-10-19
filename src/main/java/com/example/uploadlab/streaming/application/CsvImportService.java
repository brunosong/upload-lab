package com.example.uploadlab.streaming.application;

import com.example.uploadlab.streaming.application.port.in.CsvImportUseCase;
import com.example.uploadlab.streaming.application.port.out.CdnFileRowPort;
import com.example.uploadlab.streaming.application.port.out.ImportRecordPort;
import com.example.uploadlab.streaming.domain.CdnFileColumns;
import com.example.uploadlab.streaming.domain.CdnFileRow;
import com.example.uploadlab.streaming.domain.CsvImport;
import com.example.uploadlab.streaming.domain.CsvRowReader;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * CSV를 받으면서 BATCH_SIZE 행씩 모아 DB에 넣는다.
 *
 * 트랜잭션 하나로 묶지 않는다. 묶으면 업로드가 끝날 때까지(몇 분) DB 연결 하나를 붙잡는다.
 * 대신 1000행마다 연결을 잠깐 빌려 넣고 돌려준다.
 * 그래서 중간에 실패하면 이미 들어간 행이 남는다. 그 CSV의 행을 import_id로 찾아 지운다.
 */
@Service
public class CsvImportService implements CsvImportUseCase {

    private static final Logger log = LoggerFactory.getLogger(CsvImportService.class);

    private static final int BATCH_SIZE = 1000;
    private static final int PROGRESS_EVERY = 100_000;
    private static final int SHOW_BAD = 5;
    private static final int MAX_CSV_ROW_CHARS = 1_000_000;

    private final ImportRecordPort imports;
    private final CdnFileRowPort files;

    public CsvImportService(ImportRecordPort imports, CdnFileRowPort files) {
        this.imports = imports;
        this.files = files;
    }

    @Override
    public CsvImport importCsv(String originalName, InputStream body) throws IOException {
        CsvImport current = CsvImport.start(UUID.randomUUID().toString(), originalName, now());
        imports.save(current);   // 넣는 중에도 IMPORTING 으로 보인다

        String tag = "[import " + current.id().substring(0, 8) + "]";
        CountingInputStream in = new CountingInputStream(body);
        long start = System.currentTimeMillis();
        log.info("{} 시작: {}", tag, originalName);

        long saved = 0;
        long bad = 0;
        try (CsvRowReader reader = new CsvRowReader(new InputStreamReader(in, StandardCharsets.UTF_8), MAX_CSV_ROW_CHARS)) {
            CdnFileColumns columns = CdnFileColumns.from(reader.next());
            log.info("{} 헤더 확인: {}", tag, columns);

            List<CdnFileRow> batch = new ArrayList<>(BATCH_SIZE);
            long rowNumber = 0;
            List<String> row;
            while ((row = reader.next()) != null) {
                if (row.size() == 1 && row.get(0).isEmpty()) continue;   // 빈 줄
                rowNumber++;

                try {
                    batch.add(columns.toRow(row));
                } catch (IllegalArgumentException e) {   // 틀린 행은 건너뛰고 센다
                    bad++;
                    if (bad <= SHOW_BAD) log.warn("{} {}번째 행 건너뜀: {} {}", tag, rowNumber, e.getMessage(), row);
                    continue;
                }

                if (batch.size() == BATCH_SIZE) {
                    files.saveAll(current.id(), batch);   // 여기서만 DB 연결을 잠깐 빌린다
                    saved += batch.size();
                    batch.clear();
                    if (saved == BATCH_SIZE || saved % PROGRESS_EVERY == 0) {
                        log.info("{} {}행 넣음, 받은 {}, {}초", tag, String.format("%,d", saved), mb(in.count), seconds(start));
                    }
                }
            }
            if (!batch.isEmpty()) {
                files.saveAll(current.id(), batch);
                saved += batch.size();
            }
        } catch (IOException | RuntimeException e) {   // 형식 오류, 업로드 끊김, DB 오류 모두
            long deleted = files.deleteByImportId(current.id());
            imports.save(current.fail(bad, e.getMessage(), now()));
            log.warn("{} 실패. 넣었던 {}행을 지웠습니다: {}", tag, String.format("%,d", deleted), e.getMessage());
            throw e;
        }

        CsvImport done = current.complete(saved, bad, now());
        imports.save(done);
        log.info("{} 끝: {}행 넣음, 건너뛴 행 {}개, 받은 {}, {}초",
                tag, String.format("%,d", saved), bad, mb(in.count), seconds(start));
        return done;
    }

    @Override
    public List<CsvImport> findAll() {
        return imports.findAll();
    }

    /** DB TIMESTAMP 는 마이크로초까지만 저장한다. 잘라 두지 않으면 돌려준 값과 DB에서 읽은 값이 달라진다 */
    private static LocalDateTime now() {
        return LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
    }

    private static String mb(long bytes) {
        return String.format("%.1f MB", bytes / 1024.0 / 1024.0);
    }

    private static String seconds(long start) {
        return String.format("%.1f", (System.currentTimeMillis() - start) / 1000.0);
    }
}
