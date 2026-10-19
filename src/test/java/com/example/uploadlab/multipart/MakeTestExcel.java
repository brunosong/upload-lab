package com.example.uploadlab.multipart;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 엑셀 힙 비교 실험(README 11장)용 list.xlsx 를 만든다. 테스트가 아니라 main 으로 돌린다.
 *
 *   mvnw -q test-compile exec:java -Dexec.classpathScope=test
 *        -Dexec.mainClass=com.example.uploadlab.multipart.MakeTestExcel -Dexec.args="docs/test-data/list.xlsx 200000"
 *
 * docs/test-data/list.csv 와 같은 칸(path, size, memo)에 type 칸을 더했다.
 * 엑셀처럼 글자를 sharedStrings.xml 에 모은다. path, memo 는 행마다 달라 사전이 크고, type 은 세 가지뿐이다.
 */
public class MakeTestExcel {

    private static final String[] TYPES = {"jpg", "png", "webp"};

    public static void main(String[] args) throws Exception {
        Path target = Path.of(args.length > 0 ? args[0] : "docs/test-data/list.xlsx");
        int rows = args.length > 1 ? Integer.parseInt(args[1]) : 200_000;

        // 100행만 메모리에 두고 나머지는 임시 파일로 내보낸다. 마지막 true: 글자를 sharedStrings.xml 에 모은다
        try (SXSSFWorkbook wb = new SXSSFWorkbook(new XSSFWorkbook(), 100, true, true);
             OutputStream out = Files.newOutputStream(target)) {
            Sheet sheet = wb.createSheet("list");
            Row header = sheet.createRow(0);
            String[] names = {"path", "size", "memo", "type"};
            for (int c = 0; c < names.length; c++) {
                header.createCell(c).setCellValue(names[c]);
            }
            for (int i = 0; i < rows; i++) {
                String type = TYPES[i % TYPES.length];
                Row row = sheet.createRow(i + 1);
                row.createCell(0).setCellValue(String.format("/cdn/2026/10/%08d/photo_%d.%s", i, i, type));
                row.createCell(1).setCellValue(i * 37L % 9_999_999);
                row.createCell(2).setCellValue("메모" + i);
                row.createCell(3).setCellValue(type);
            }
            wb.write(out);
        }
        System.out.println(target.toAbsolutePath() + " (" + Files.size(target) / 1024 / 1024 + " MB, " + rows + "행)");
    }
}
