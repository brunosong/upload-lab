package com.example.uploadlab.streaming.adapter.out.db;

import com.example.uploadlab.streaming.application.port.out.CdnFileRowPort;
import com.example.uploadlab.streaming.domain.CdnFileRow;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * CSV 행을 cdn_file 테이블에 넣는다.
 *
 * batchUpdate: INSERT 1000번을 한 번에 묶어 보낸다. 한 줄씩 update() 하면 1000번 왕복한다.
 * JPA saveAll()은 id가 IDENTITY(AUTO_INCREMENT)면 묶어 보내지 못해서 대량 입력에는 JdbcTemplate을 쓴다.
 */
@Component
public class JdbcCdnFileRowAdapter implements CdnFileRowPort {

    private final JdbcTemplate jdbc;

    public JdbcCdnFileRowAdapter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void saveAll(String importId, List<CdnFileRow> rows) {
        jdbc.batchUpdate("INSERT INTO cdn_file (import_id, path, size) VALUES (?, ?, ?)",
                rows, rows.size(), (ps, row) -> {
                    ps.setString(1, importId);
                    ps.setString(2, row.path());
                    ps.setLong(3, row.size());
                });
    }

    @Override
    public long deleteByImportId(String importId) {
        return jdbc.update("DELETE FROM cdn_file WHERE import_id = ?", importId);
    }

    @Override
    public long countByImportId(String importId) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM cdn_file WHERE import_id = ?", Long.class, importId);
        return count == null ? 0 : count;
    }
}
