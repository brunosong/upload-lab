package com.example.uploadlab.multipart;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.XMLHelper;
import org.apache.poi.xssf.eventusermodel.ReadOnlySharedStringsTable;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler;
import org.apache.poi.xssf.eventusermodel.XSSFSheetXMLHandler.SheetContentsHandler;
import org.apache.poi.xssf.usermodel.XSSFComment;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.InputStream;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 실험용: multipart로 받은 엑셀을 세 가지 방법으로 읽고 힙을 잰다. (README 11장)
 *
 *   /workbook-stream  new XSSFWorkbook(InputStream)  ZIP을 메모리에 풀고 + 모든 셀을 객체로
 *   /workbook-file    new XSSFWorkbook(File)         ZIP은 필요한 부분만 + 모든 셀을 객체로
 *   /sax              XSSFReader + SAX (File)        ZIP은 필요한 부분만 + 지금 읽는 셀만
 *
 * 세 방법 모두 첫 시트의 모든 셀을 글자로 바꿔 읽는다. 하는 일은 같고 읽는 방법만 다르다.
 */
@RestController
@RequestMapping("/api/multipart/excel")
public class ExcelHeapExperimentController {

    @PostMapping("/workbook-stream")
    public Report workbookStream(@RequestParam("file") MultipartFile file) {
        return measure("XSSFWorkbook(InputStream)", file, () -> {
            try (InputStream in = file.getInputStream();
                 Workbook wb = new XSSFWorkbook(in)) {
                return readAll(wb);
            }
        });
    }

    @PostMapping("/workbook-file")
    public Report workbookFile(@RequestParam("file") MultipartFile file) {
        return measure("XSSFWorkbook(File)", file, () -> withTempFile(file, tmp -> {
            // READ로 연다. new XSSFWorkbook(File) 은 쓰기 모드로 열어서 닫을 때 원본을 건드릴 수 있다
            try (OPCPackage pkg = OPCPackage.open(tmp.toFile(), PackageAccess.READ);
                 Workbook wb = new XSSFWorkbook(pkg)) {
                return readAll(wb);
            }
        }));
    }

    @PostMapping("/sax")
    public Report sax(@RequestParam("file") MultipartFile file) {
        return measure("SAX (XSSFReader)", file, () -> withTempFile(file, tmp -> {
            try (OPCPackage pkg = OPCPackage.open(tmp.toFile(), PackageAccess.READ)) {
                ReadOnlySharedStringsTable strings = new ReadOnlySharedStringsTable(pkg);   // 글자 사전. 이건 메모리에 올라간다
                XSSFReader reader = new XSSFReader(pkg);
                Counter counter = new Counter();

                try (InputStream sheet = reader.getSheetsData().next()) {   // 첫 시트의 XML을 흘려 읽는다
                    XMLReader parser = XMLHelper.newXMLReader();
                    parser.setContentHandler(new XSSFSheetXMLHandler(
                            reader.getStylesTable(), strings, counter, new DataFormatter(), false));
                    parser.parse(new InputSource(sheet));
                }
                counter.retainedMb = retainedHeapMb();   // 사전이 아직 살아 있을 때 잰다
                return counter.result();
            }
        }));
    }

    /** 첫 시트의 모든 셀을 글자로 바꿔 본다. 다 읽은 뒤 workbook 을 아직 쥐고 있을 때 힙을 잰다 */
    private Result readAll(Workbook wb) {
        DataFormatter formatter = new DataFormatter();
        Sheet sheet = wb.getSheetAt(0);
        long rows = 0;
        long cells = 0;
        for (Row row : sheet) {
            rows++;
            for (Cell cell : row) {
                formatter.formatCellValue(cell);
                cells++;
            }
        }
        return new Result(rows, cells, retainedHeapMb());
    }

    /** SAX: 셀이 하나 지나갈 때마다 불린다. 받은 값을 세기만 하고 버린다 */
    private static class Counter implements SheetContentsHandler {
        long rows;
        long cells;
        long retainedMb;

        @Override
        public void startRow(int rowNum) {
            rows++;
        }

        @Override
        public void endRow(int rowNum) {
        }

        @Override
        public void cell(String cellReference, String formattedValue, XSSFComment comment) {
            cells++;
        }

        Result result() {
            return new Result(rows, cells, retainedMb);
        }
    }

    // ---- 재는 부분 ----

    private interface Work {
        Result run() throws Exception;
    }

    private interface TempFileWork {
        Result run(Path tmp) throws Exception;
    }

    /** MultipartFile은 톰캣 임시 파일 경로를 알려 주지 않는다. 내 임시 파일로 옮긴 뒤 File로 연다 */
    private Result withTempFile(MultipartFile file, TempFileWork work) throws Exception {
        Path tmp = Files.createTempFile("excel-", ".xlsx");
        try {
            file.transferTo(tmp);
            return work.run(tmp);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private Report measure(String method, MultipartFile file, Work work) {
        long before = retainedHeapMb();
        HeapSampler sampler = new HeapSampler();
        long start = System.currentTimeMillis();
        try {
            Result r = work.run();
            return new Report(method, file.getSize() / 1024 / 1024, r.rows, r.cells,
                    before, sampler.stop(), r.retainedMb, maxHeapMb(), System.currentTimeMillis() - start, null);
        } catch (OutOfMemoryError e) {
            // 실험이라 잡아서 보여준다. 실제 코드에서는 OutOfMemoryError를 잡지 않는다
            return new Report(method, file.getSize() / 1024 / 1024, 0, 0,
                    before, sampler.stop(), -1, maxHeapMb(), System.currentTimeMillis() - start, e.toString());
        } catch (Exception e) {
            return new Report(method, file.getSize() / 1024 / 1024, 0, 0,
                    before, sampler.stop(), -1, maxHeapMb(), System.currentTimeMillis() - start, e.toString());
        }
    }

    /** GC를 한 다음 잰다. 아직 쥐고 있는(살아 있는) 메모리만 남는다 */
    private static long retainedHeapMb() {
        System.gc();
        return usedHeapMb();
    }

    private static long usedHeapMb() {
        MemoryMXBean memory = ManagementFactory.getMemoryMXBean();
        return memory.getHeapMemoryUsage().getUsed() / 1024 / 1024;
    }

    private static long maxHeapMb() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    /** 작업하는 동안 10ms마다 힙 사용량을 보고 가장 큰 값을 기억한다. 아직 안 치운 쓰레기도 포함된다 */
    private static class HeapSampler {
        private volatile boolean running = true;
        private volatile long peakMb = usedHeapMb();
        private final Thread thread;

        HeapSampler() {
            thread = Thread.ofPlatform().daemon().start(() -> {
                while (running) {
                    peakMb = Math.max(peakMb, usedHeapMb());
                    try {
                        Thread.sleep(10);
                    } catch (InterruptedException e) {
                        return;
                    }
                }
            });
        }

        long stop() {
            running = false;
            try {
                thread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return peakMb;
        }
    }

    private record Result(long rows, long cells, long retainedMb) {
    }

    public record Report(
            String method,
            long fileMb,
            long rows,
            long cells,
            long heapBeforeMb,    // 시작 전 (GC 후)
            long heapPeakMb,      // 읽는 동안 가장 컸던 값 (쓰레기 포함)
            long heapRetainedMb,  // 다 읽고 workbook/사전을 아직 쥐고 있을 때 (GC 후). 진짜로 붙잡고 있는 양
            long maxHeapMb,
            long elapsedMs,
            String error          // 실패했으면 이유 (OutOfMemoryError 등)
    ) {
    }
}
