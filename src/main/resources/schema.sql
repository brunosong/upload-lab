-- CSV 한 번 넣기 = 한 건. 진행 상태와 결과를 남긴다
CREATE TABLE IF NOT EXISTS csv_import (
    id            VARCHAR(36)   PRIMARY KEY,
    original_name VARCHAR(255)  NOT NULL,
    status        VARCHAR(20)   NOT NULL,   -- IMPORTING / COMPLETED / FAILED
    saved_rows    BIGINT        NOT NULL,
    bad_rows      BIGINT        NOT NULL,
    error         VARCHAR(1000),
    started_at    TIMESTAMP     NOT NULL,
    finished_at   TIMESTAMP
);

-- CSV 한 행 = 한 줄. 어느 CSV에서 왔는지 import_id 로 묶는다
CREATE TABLE IF NOT EXISTS cdn_file (
    id        BIGINT        AUTO_INCREMENT PRIMARY KEY,
    import_id VARCHAR(36)   NOT NULL,
    path      VARCHAR(1000) NOT NULL,
    size      BIGINT        NOT NULL
);

-- 실패했을 때 그 CSV에서 넣은 행만 지우고, 건수를 세는 데 쓴다
CREATE INDEX IF NOT EXISTS idx_cdn_file_import_id ON cdn_file (import_id);
