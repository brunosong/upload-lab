package com.example.uploadlab.streaming.domain;

import java.io.BufferedReader;
import java.io.Closeable;
import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.List;

/**
 * CSV를 한 행씩 읽는다. 메모리에는 지금 읽는 한 행만 있다.
 *
 * line.split(",") 으로는 안 되는 경우가 있어서 글자를 하나씩 본다.
 *   "서울, 강남"       따옴표 안의 쉼표는 칸 구분이 아니다
 *   "말 ""인용"" 끝"   따옴표 안의 "" 는 따옴표 글자 하나다
 *   "첫 줄
 *    둘째 줄"          따옴표 안의 줄바꿈은 행의 끝이 아니다. readLine() 으로 읽으면 행이 둘로 쪼개진다
 */
public class CsvRowReader implements Closeable {

    private final Reader in;
    private final int maxRowChars;
    private long lineNumber = 1;   // 오류 메시지용. 지금 읽는 줄 번호
    private boolean first = true;

    public CsvRowReader(Reader in, int maxRowChars) {
        this.in = new BufferedReader(in);   // read()로 한 글자씩 부르므로 버퍼가 꼭 있어야 한다
        this.maxRowChars = maxRowChars;
    }

    /** 다음 행의 칸들. 파일이 끝나면 null */
    public List<String> next() throws IOException {
        int c = in.read();
        if (first) {
            first = false;
            if (c == '\uFEFF') c = in.read();   // 엑셀이 UTF-8로 저장한 CSV 맨 앞에 붙는 BOM
        }
        if (c == -1) return null;

        long startLine = lineNumber;
        List<String> fields = new ArrayList<>();
        StringBuilder field = new StringBuilder();
        boolean inQuotes = false;
        int rowChars = 0;

        while (true) {
            if (c == -1) {
                if (inQuotes) {
                    throw new IllegalArgumentException(startLine + "번째 줄에서 연 따옴표가 닫히지 않은 채 파일이 끝났습니다");
                }
                fields.add(field.toString());
                return fields;
            }
            if (++rowChars > maxRowChars) {   // 줄바꿈 없는 1GB가 오면 StringBuilder가 1GB가 된다. 그 전에 끊는다
                throw new IllegalArgumentException(startLine + "번째 줄의 행이 " + maxRowChars + "글자를 넘습니다");
            }
            if (c == '\n') lineNumber++;

            if (inQuotes) {
                if (c == '"') {
                    int nextChar = in.read();
                    if (nextChar == '"') {
                        field.append('"');        // "" 는 " 한 글자
                    } else {
                        inQuotes = false;         // 닫는 따옴표였다. 방금 읽은 글자는 위로 돌아가 다시 처리한다
                        c = nextChar;
                        continue;
                    }
                } else {
                    field.append((char) c);       // 따옴표 안에서는 쉼표, 줄바꿈도 그냥 글자
                }
            } else if (c == '"' && field.isEmpty()) {
                inQuotes = true;
            } else if (c == ',') {
                fields.add(field.toString());
                field.setLength(0);
            } else if (c == '\n') {
                fields.add(field.toString());
                return fields;
            } else if (c != '\r') {               // \r\n 의 \r 은 버린다
                field.append((char) c);
            }
            c = in.read();
        }
    }

    @Override
    public void close() throws IOException {
        in.close();
    }
}
