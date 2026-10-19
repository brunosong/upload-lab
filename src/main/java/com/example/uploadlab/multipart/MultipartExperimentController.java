package com.example.uploadlab.multipart;

import jakarta.servlet.ServletContext;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 실험용: 흔히 쓰는 MultipartFile로 큰 파일을 받으면 서버에서 무슨 일이 일어나는지 본다.
 * 업로드 "방식"이 아니라 비교 실험이라 헥사고날로 나누지 않았다.
 *
 *   /transfer  정석. transferTo로 저장한다
 *   /bytes     하면 안 되는 것. getBytes()로 힙에 통째로 올린다
 */
@RestController
@RequestMapping("/api/multipart")
public class MultipartExperimentController {

    private final Path saveDir;

    public MultipartExperimentController(@Value("${lab.upload.dir}") String uploadDir) {
        this.saveDir = Path.of(uploadDir).toAbsolutePath();
    }

    @PostMapping("/transfer")
    public Report transfer(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        long enteredAt = System.currentTimeMillis();
        List<String> tempFiles = tempFiles(request);   // 컨트롤러에 들어온 순간, 톰캣 임시 폴더에 이미 뭐가 있나
        long heapBefore = usedHeapMb();

        Path target = newTarget();
        long start = System.currentTimeMillis();
        file.transferTo(target.toFile());   // 같은 드라이브면 이름만 바꾸고, 다른 드라이브면 한 번 더 복사한다
        long workMillis = System.currentTimeMillis() - start;

        return new Report("transferTo", file.getSize(), waited(request, enteredAt), tempFiles,
                workMillis, heapBefore, usedHeapMb(), maxHeapMb());
    }

    @PostMapping("/bytes")
    public Report bytes(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws IOException {
        long enteredAt = System.currentTimeMillis();
        List<String> tempFiles = tempFiles(request);
        long heapBefore = usedHeapMb();

        long start = System.currentTimeMillis();
        byte[] all = file.getBytes();       // 디스크에 잘 있던 파일을 힙으로 통째로 읽는다. 힙이 작으면 OutOfMemoryError
        long heapAfter = usedHeapMb();      // all을 아직 들고 있을 때 잰다
        Files.write(newTarget(), all);
        long workMillis = System.currentTimeMillis() - start;

        return new Report("getBytes", all.length, waited(request, enteredAt), tempFiles,
                workMillis, heapBefore, heapAfter, maxHeapMb());
    }

    /** 필터가 요청을 받은 뒤 컨트롤러에 들어오기까지 걸린 시간. multipart를 푸는 시간이다 */
    private long waited(HttpServletRequest request, long enteredAt) {
        Object arrivedAt = request.getAttribute(ArrivalTimeFilter.ARRIVED_AT);
        return arrivedAt == null ? -1 : enteredAt - (long) arrivedAt;
    }

    /** 톰캣은 multipart 본문을 이 폴더에 upload_*.tmp 로 받아 둔다 */
    private List<String> tempFiles(HttpServletRequest request) throws IOException {
        File dir = (File) request.getServletContext().getAttribute(ServletContext.TEMPDIR);
        List<String> found = new ArrayList<>();
        try (var files = Files.list(dir.toPath())) {
            for (Path p : files.filter(p -> p.getFileName().toString().startsWith("upload_")).toList()) {
                found.add(p + " (" + Files.size(p) / 1024 / 1024 + " MB)");
            }
        }
        return found;
    }

    private Path newTarget() throws IOException {
        Files.createDirectories(saveDir);
        return saveDir.resolve("multipart-" + UUID.randomUUID() + ".dat");
    }

    /** 잴 때마다 쓰레기가 섞이지 않게 GC를 먼저 한 번 부른다. 실험용이라서 하는 것이다 */
    private long usedHeapMb() {
        System.gc();
        Runtime rt = Runtime.getRuntime();
        return (rt.totalMemory() - rt.freeMemory()) / 1024 / 1024;
    }

    private long maxHeapMb() {
        return Runtime.getRuntime().maxMemory() / 1024 / 1024;
    }

    public record Report(
            String method,                 // transferTo / getBytes
            long size,                     // 받은 바이트 수
            long waitedBeforeControllerMs, // 요청 도착 → 컨트롤러 진입. 이 동안 톰캣이 본문을 임시 파일로 받는다
            List<String> tempFilesOnEnter, // 컨트롤러에 들어온 순간의 톰캣 임시 파일
            long workMs,                   // 컨트롤러 안에서 저장하는 데 걸린 시간
            long heapBeforeMb,
            long heapAfterMb,
            long maxHeapMb
    ) {
    }
}
