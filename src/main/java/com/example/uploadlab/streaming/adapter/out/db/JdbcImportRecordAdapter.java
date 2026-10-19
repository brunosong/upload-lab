package com.example.uploadlab.streaming.adapter.out.db;

import com.example.uploadlab.streaming.application.port.out.ImportRecordPort;
import com.example.uploadlab.streaming.domain.CsvImport;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * CSV 넣기 기록을 csv_import 테이블에 둔다.
 */
@Component
public class JdbcImportRecordAdapter implements ImportRecordPort {

    private final JdbcTemplate jdbc;

    public JdbcImportRecordAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void save(CsvImport c) {
        // H2의 MERGE ... KEY: id가 없으면 넣고, 있으면 고친다
        jdbc.update("""
                MERGE INTO csv_import (id, original_name, status, saved_rows, bad_rows, error, started_at, finished_at)
                KEY (id) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                c.id(), c.originalName(), c.status().name(), c.savedRows(), c.badRows(), c.error(),
                c.startedAt(), c.finishedAt());
    }

    @Override
    public List<CsvImport> findAll() {
        return jdbc.query("SELECT * FROM csv_import ORDER BY started_at DESC", (rs, n) -> new CsvImport(
                rs.getString("id"),
                rs.getString("original_name"),
                CsvImport.Status.valueOf(rs.getString("status")),
                rs.getLong("saved_rows"),
                rs.getLong("bad_rows"),
                rs.getString("error"),
                rs.getObject("started_at", LocalDateTime.class),
                rs.getObject("finished_at", LocalDateTime.class)));
    }
}
