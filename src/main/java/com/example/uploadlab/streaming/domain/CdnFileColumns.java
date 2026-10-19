package com.example.uploadlab.streaming.domain;

import java.util.List;

/**
 * 헤더에서 path, size 가 몇 번째 칸인지 기억했다가, 행 하나를 CdnFileRow 로 바꾼다.
 * 칸 순서는 상관없고, 다른 칸(memo 등)은 무시한다.
 */
public class CdnFileColumns {

    private static final int MAX_PATH_LENGTH = 1000;   // cdn_file.path 컬럼 길이

    private final int pathIndex;
    private final int sizeIndex;
    private final int columnCount;

    private CdnFileColumns(int pathIndex, int sizeIndex, int columnCount) {
        this.pathIndex = pathIndex;
        this.sizeIndex = sizeIndex;
        this.columnCount = columnCount;
    }

    public static CdnFileColumns from(List<String> header) {
        if (header == null) {
            throw new IllegalArgumentException("빈 파일입니다");
        }
        int path = indexOf(header, "path");
        int size = indexOf(header, "size");
        if (path < 0 || size < 0) {
            throw new IllegalArgumentException("헤더에 path, size 칸이 있어야 합니다: " + header);
        }
        return new CdnFileColumns(path, size, header.size());
    }

    /** 틀린 행이면 IllegalArgumentException. 메시지가 건너뛴 이유다 */
    public CdnFileRow toRow(List<String> row) {
        if (row.size() != columnCount) {
            throw new IllegalArgumentException("칸이 " + row.size() + "개입니다 (헤더는 " + columnCount + "개)");
        }
        String path = row.get(pathIndex).trim();
        if (path.isEmpty()) {
            throw new IllegalArgumentException("path가 비었습니다");
        }
        if (path.length() > MAX_PATH_LENGTH) {
            throw new IllegalArgumentException("path가 " + MAX_PATH_LENGTH + "글자를 넘습니다");
        }

        long size;
        try {
            size = Long.parseLong(row.get(sizeIndex).trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("size가 숫자가 아닙니다: " + row.get(sizeIndex));
        }
        if (size < 0) {
            throw new IllegalArgumentException("size가 음수입니다: " + size);
        }
        return new CdnFileRow(path, size);
    }

    private static int indexOf(List<String> header, String name) {
        for (int i = 0; i < header.size(); i++) {
            if (header.get(i).trim().equalsIgnoreCase(name)) return i;
        }
        return -1;
    }

    @Override
    public String toString() {
        return "path=" + pathIndex + "번째 칸, size=" + sizeIndex + "번째 칸 (0부터), 전체 " + columnCount + "칸";
    }
}
