package com.example.uploadlab.streaming.domain;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CdnFileColumnsTest {

    @Test
    void 칸_순서가_달라도_이름으로_찾고_다른_칸은_무시한다() {
        CdnFileColumns columns = CdnFileColumns.from(List.of("memo", " SIZE ", "path"));

        assertThat(columns.toRow(List.of("메모", "100", "/a.jpg"))).isEqualTo(new CdnFileRow("/a.jpg", 100));
    }

    @Test
    void 헤더에_path나_size가_없으면_거절한다() {
        assertThatThrownBy(() -> CdnFileColumns.from(List.of("path", "bytes")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("path, size");
        assertThatThrownBy(() -> CdnFileColumns.from(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("빈 파일");
    }

    @Test
    void 틀린_행은_이유와_함께_거절한다() {
        CdnFileColumns columns = CdnFileColumns.from(List.of("path", "size"));

        assertThatThrownBy(() -> columns.toRow(List.of("/a.jpg"))).hasMessageContaining("칸이 1개");
        assertThatThrownBy(() -> columns.toRow(List.of(" ", "1"))).hasMessageContaining("path가 비었");
        assertThatThrownBy(() -> columns.toRow(List.of("/a.jpg", "12KB"))).hasMessageContaining("숫자가 아닙니다");
        assertThatThrownBy(() -> columns.toRow(List.of("/a.jpg", "-1"))).hasMessageContaining("음수");
        assertThatThrownBy(() -> columns.toRow(List.of("/" + "a".repeat(1000), "1"))).hasMessageContaining("1000글자");
    }
}
