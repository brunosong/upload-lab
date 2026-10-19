package com.example.uploadlab.streaming.domain;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CsvRowReaderTest {

    @Test
    void 쉼표로_칸을_나눈다() throws IOException {
        assertThat(readAll("a,b,c\n1,2,3\n")).containsExactly(
                List.of("a", "b", "c"),
                List.of("1", "2", "3"));
    }

    @Test
    void 따옴표_안의_쉼표는_칸_구분이_아니다() throws IOException {
        assertThat(readAll("이름,주소\n홍길동,\"서울, 강남\"\n")).containsExactly(
                List.of("이름", "주소"),
                List.of("홍길동", "서울, 강남"));
    }

    @Test
    void 따옴표_두_개는_따옴표_글자_하나다() throws IOException {
        assertThat(readAll("\"말 \"\"인용\"\" 끝\",x\n")).containsExactly(
                List.of("말 \"인용\" 끝", "x"));
    }

    @Test
    void 따옴표_안의_줄바꿈은_행의_끝이_아니다() throws IOException {
        assertThat(readAll("메모,n\n\"첫 줄\n둘째 줄\",1\n다음,2\n")).containsExactly(
                List.of("메모", "n"),
                List.of("첫 줄\n둘째 줄", "1"),
                List.of("다음", "2"));
    }

    @Test
    void 윈도우_줄바꿈과_BOM과_마지막_줄바꿈_없음을_처리한다() throws IOException {
        assertThat(readAll("\uFEFFa,b\r\n1,2")).containsExactly(
                List.of("a", "b"),
                List.of("1", "2"));
    }

    @Test
    void 빈_칸도_칸이다() throws IOException {
        assertThat(readAll("a,,\"\"\n")).containsExactly(List.of("a", "", ""));
    }

    @Test
    void 닫히지_않은_따옴표는_몇번째_줄인지_알려준다() {
        assertThatThrownBy(() -> readAll("a\nb\n\"열고 안 닫음\n끝"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("3번째 줄");
    }

    @Test
    void 너무_긴_행은_끝까지_모으지_않고_끊는다() {
        assertThatThrownBy(() -> new CsvRowReader(new StringReader("x".repeat(101)), 100).next())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("100글자");
    }

    private List<List<String>> readAll(String csv) throws IOException {
        List<List<String>> rows = new ArrayList<>();
        try (CsvRowReader reader = new CsvRowReader(new StringReader(csv), 1000)) {
            List<String> row;
            while ((row = reader.next()) != null) {
                rows.add(row);
            }
        }
        return rows;
    }
}
