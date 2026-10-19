# 대용량 업로드 연습장 (upload-lab)

CDN 스토리지 이관 작업에서 업체가 주는 1GB 안팎의 파일 목록을 올리는 상황을 연습하는 프로젝트입니다. 1GB 파일을 메모리에 올리지 않고 서버에 보내는 방법을 기능별로 나눠서 만듭니다.

관련 위키 문서: `D:\WIKI\브루노송\CDN 스토리지 이관\대용량 목록 파일 처리.md`

방식은 두 가지입니다. 스트리밍 방식(`streaming` 패키지)과 파일 쪼개는 방식(`chunked` 패키지)입니다.

---

## 1. 실행

    cd D:\STUDY\upload-lab
    mvnw spring-boot:run

- 화면: http://localhost:8098
- 메인 화면에서 "스트리밍 방식"과 "파일 쪼개는 방식" 중 하나를 골라 들어갑니다. 기능마다 페이지가 따로 있습니다.
- 테스트: `mvnw test`
- 올린 파일은 프로젝트 폴더의 `uploads/`에 쌓입니다.
- CSV를 DB에 넣으면 H2 파일 DB `uploads/h2/labdb.mv.db`에 남습니다. http://localhost:8098/h2-console 에서 봅니다 (JDBC URL `jdbc:h2:file:./uploads/h2/labdb`, 사용자 `sa`, 비밀번호 없음).
- 9장, 10장에 쓰는 테스트 데이터는 `docs/test-data/`에 있습니다. 큰 파일은 git에 없어서 `python make_test_data.py`로 만듭니다.
- 업로드 기록을 어디에 남길지는 `application.properties`의 `lab.record.type`으로 고릅니다. `memory`(기본값)는 서버를 끄면 사라지고, `file`은 `uploads/records.jsonl`에 남습니다. 실행할 때 `mvnw spring-boot:run -Dspring-boot.run.arguments=--lab.record.type=file`로 바꿀 수도 있습니다.

테스트용 1GB 파일은 Windows에서 이렇게 만듭니다. 내용은 전부 0입니다.

    fsutil file createnew test-1g.bin 1073741824

서버가 계산한 SHA-256이 맞는지는 이렇게 확인합니다.

    certutil -hashfile test-1g.bin SHA256

---

## 2. 폴더 구조

기능 하나가 패키지 하나입니다. **한 기능을 보려면 그 패키지 하나만 열면 됩니다.** 기능끼리 코드가 겹쳐도 공통으로 빼지 않고 각자 가집니다. 기능 안은 헥사고날 구조로 나눴습니다.

```
com.example.uploadlab
├── UploadLabApplication.java
└── streaming/                                   스트리밍 방식 (이 폴더만 보면 다 있음)
    ├── domain/
    │   ├── UploadedFile.java                    업로드 결과 한 건. 스프링을 모르는 순수 값
    │   ├── CsvRowReader.java                    CSV를 글자 하나씩 읽어 한 행씩 돌려줌 (9장)
    │   ├── TextReadResult.java                  텍스트를 받으면서 읽은 결과 (9장)
    │   ├── CsvReadResult.java                   CSV를 받으면서 읽은 결과 (9장)
    │   ├── CsvImport.java                       CSV 한 번 넣기. IMPORTING → COMPLETED / FAILED (10장)
    │   ├── CdnFileColumns.java                  헤더에서 path, size 위치를 찾고 행을 검사 (10장)
    │   └── CdnFileRow.java                      DB에 넣을 한 행 (10장)
    ├── application/
    │   ├── port/in/StreamUploadUseCase.java     바깥이 이 기능에 시키는 일
    │   ├── port/in/StreamReadUseCase.java       받으면서 읽기. 저장하지 않음 (9장)
    │   ├── port/in/CsvImportUseCase.java        받으면서 DB에 넣기 (10장)
    │   ├── port/out/FileStoragePort.java        어디에 저장할지 (구현은 어댑터)
    │   ├── port/out/UploadRecordPort.java       어디에 기록할지 (구현은 어댑터)
    │   ├── port/out/ImportRecordPort.java       넣기 기록을 어디에 둘지 (10장)
    │   ├── port/out/CdnFileRowPort.java         행을 어디에 넣고 지울지 (10장)
    │   ├── StreamUploadService.java             흐름만 담음. 디스크, 메모리를 직접 모름
    │   ├── StreamReadService.java               한 줄씩 읽고 로그로 남긴 뒤 버림 (9장)
    │   ├── CsvImportService.java                1000행씩 모아 넣고, 실패하면 넣은 행을 지움 (10장)
    │   └── CountingInputStream.java             받은 바이트 수를 셈. 로그용 (9, 10장)
    └── adapter/
        ├── in/web/StreamUploadController.java   HTTP 요청을 유스케이스 호출로 바꿈
        ├── in/web/StreamReadController.java     /api/streaming/read/text, /csv (9장)
        ├── in/web/CsvImportController.java      /api/streaming/imports (10장)
        ├── in/web/StreamReadExceptionHandler.java   형식 오류를 400으로 바꿈 (9, 10장)
        ├── out/storage/LocalDiskStorageAdapter.java     8KB씩 받아 디스크에 씀
        ├── out/record/InMemoryUploadRecordAdapter.java  기록을 메모리에 둠 (lab.record.type=memory)
        ├── out/record/FileUploadRecordAdapter.java      기록을 records.jsonl 에 둠 (lab.record.type=file)
        ├── out/db/JdbcImportRecordAdapter.java  넣기 기록을 csv_import 테이블에 둠 (10장)
        └── out/db/JdbcCdnFileRowAdapter.java    행을 cdn_file 테이블에 batchUpdate 로 넣음 (10장)

com.example.uploadlab
└── chunked/                                     파일 쪼개는 방식 (이 폴더만 보면 다 있음)
    ├── domain/
    │   ├── UploadSession.java                   업로드 한 건의 진행 상태. 몇 번 조각을 받았는지, 다 모였는지 판단
    │   └── CompletedUpload.java                 합친 결과 한 건
    ├── application/
    │   ├── in/ChunkUploadUseCase.java           시작, 조각 받기, 완료
    │   ├── out/UploadSessionPort.java           세션을 어디에 둘지
    │   ├── out/ChunkStoragePort.java            조각을 어디에 쓰고 어떻게 합칠지
    │   ├── ChunkUploadService.java              흐름만 담음
    │   └── UploadSessionNotFoundException.java
    └── adapter/
        ├── in/web/ChunkUploadController.java          HTTP 요청을 유스케이스 호출로 바꿈
        ├── in/web/ChunkUploadExceptionHandler.java    예외를 400 / 404 / 409 로 바꿈
        ├── out/session/InMemoryUploadSessionAdapter.java   세션을 메모리에 둠
        └── out/storage/LocalDiskChunkStorageAdapter.java   조각을 디스크에 쓰고 합침

com.example.uploadlab
└── multipart/                                   비교 실험. MultipartFile로 받으면 어떻게 되는지 (7장)
    ├── MultipartExperimentController.java       /transfer (transferTo), /bytes (getBytes)
    ├── ArrivalTimeFilter.java                   요청 도착 시각을 남겨 컨트롤러가 기다린 시간을 잼
    └── ExcelHeapExperimentController.java       엑셀을 세 가지 방법으로 읽고 힙을 잼 (11장)

test/.../multipart/MakeTestExcel.java            11장 실험용 list.xlsx 를 POI로 만드는 main

resources/static/
├── index.html                메인. 방식 고르기
├── streaming/index.html      스트리밍 방식 화면 (받으면서 읽기, DB에 넣기 포함)
└── chunked/index.html        파일 쪼개는 방식 화면

resources/schema.sql          시작할 때 H2에 csv_import, cdn_file 테이블을 만듦 (10장)

docs/test-data/               테스트 데이터와 만드는 스크립트 (9, 10장)
```

화면 파일은 스프링 규칙상 `resources/static` 아래에 있어야 해서 패키지 폴더 밖에 있습니다. 대신 기능 이름과 같은 폴더로 나눴습니다.

의존 방향은 항상 바깥에서 안쪽입니다.

```
Controller(in 어댑터) → UseCase(in 포트) ← Service → out 포트 ← out 어댑터(디스크, 메모리)
```

서비스는 포트 인터페이스만 압니다. 기록 어댑터가 메모리, 파일 두 개 있고 설정값으로 하나만 올라갑니다. 서비스 코드는 어느 쪽이든 그대로입니다. 어댑터를 어떻게 추가하고 교체하는지, 언제 어댑터 대신 유스케이스를 새로 만드는지는 [결정 문서](docs/결정-어댑터%20교체와%20유스케이스%20분리.md)에 정리했습니다.

---

## 3. 스트리밍 방식의 흐름

```
[브라우저]  xhr.send(file)
     │      File 객체를 그대로 넘김. 브라우저가 디스크에서 조금씩 읽어 보냄
     │      PUT /api/streaming/uploads   Content-Type: application/octet-stream
     ▼
[StreamUploadController]   request.getInputStream()  본문을 읽을 통로만 넘김
     ▼
[StreamUploadService]      ID 만들기 → 저장 포트 호출 → 기록 포트 호출
     ▼
[LocalDiskStorageAdapter]  while (8KB 읽기) { 8KB 쓰기 }
                           DigestInputStream 으로 읽으면서 SHA-256 계산
```

이 lab의 핵심은 `LocalDiskStorageAdapter.save()` 안의 while 문 하나입니다.

```java
byte[] buffer = new byte[8192];
int read;
while ((read = digestIn.read(buffer)) != -1) {   // 네트워크에서 8KB 받으면
    out.write(buffer, 0, read);                  // 바로 디스크에 쓰고, 통을 다시 쓴다
    total += read;
}
```

파일이 1KB든 1GB든 메모리에는 8KB 통 하나만 있습니다.

### 이렇게 하면 안 됩니다

```java
// 서버: 1GB를 힙에 통째로 올림 → OutOfMemoryError
byte[] all = multipartFile.getBytes();
```

```js
// 브라우저: 1GB를 탭 메모리에 통째로 올림
const buf = await file.arrayBuffer();
xhr.send(buf);
```

작은 파일로 테스트하면 문제없이 돌아가서 안 드러납니다.

### 확인해 본 결과

2026-10-14에 512MB 랜덤 파일을 curl로 올렸습니다.

| 항목 | 결과 |
|---|---|
| 받은 크기 | 536,870,912 bytes (원본과 같음) |
| SHA-256 | 원본 `sha256sum` 값과 같음 |
| 서버가 받는 데 걸린 시간 | 16.8초 (로컬, 같은 PC) |
| 업로드 직후 서버 힙 사용량 | 31MB |

512MB를 받았는데 힙은 31MB입니다. 파일 크기만큼 메모리가 늘지 않는다는 게 스트리밍의 증거입니다. 화면의 "업로드 직후 서버 힙 사용량"으로 직접 확인할 수 있습니다.

curl로 직접 올릴 때는 `-T`를 씁니다. `--data-binary @파일`은 curl이 파일을 메모리에 먼저 읽습니다.

    curl -T test-1g.bin -H "Content-Type: application/octet-stream" -H "X-File-Name: test-1g.bin" http://localhost:8098/api/streaming/uploads

---

## 4. 코드에서 볼 부분

- **multipart 대신 octet-stream**: 요청 본문이 곧 파일 내용이라 `request.getInputStream()`을 그대로 흘려 넘길 수 있습니다. multipart는 경계 문자열로 감싸져 있어서 스프링이 먼저 풀어야 합니다.
- **`DigestInputStream`**: 읽는 동안 해시를 같이 계산합니다. 저장한 파일을 다시 읽을 필요가 없습니다.
- **원래 파일 이름은 경로에 안 씀**: 저장 이름은 서버가 만든 UUID입니다. `../../` 같은 이름으로 다른 경로에 쓰는 공격을 막습니다. 한글 이름은 프론트에서 `encodeURIComponent`로 인코딩해 헤더로 보내고, 서버에서 `URLDecoder`로 풉니다.
- **`XMLHttpRequest`를 쓰는 이유**: `fetch`에는 업로드 진행률 이벤트가 없습니다. `xhr.upload.onprogress`로 진행률을 보여 줍니다.
- **스프링 없이 테스트**: `StreamUploadTest`는 서비스에 어댑터를 직접 끼워서 돌립니다. 200MB를 실제 배열 없이 흘려 보내는 가짜 스트림으로 큰 데이터도 확인합니다.

---

## 5. 이 방식의 약점

- 900MB까지 보내다 끊기면 처음부터 다시 보내야 합니다.
- 요청 하나가 수 분 걸려서 중간 장비 설정에 걸립니다. nginx `client_max_body_size` 기본값은 1MB이고, 프록시 타임아웃, Ingress 본문 크기 제한도 늘려야 합니다.
- 이 lab은 서버에 바로 붙기 때문에 위 제한이 없습니다. `application.properties`의 `server.tomcat.connection-timeout`만 넉넉히 늘려 뒀습니다.

이 약점을 푸는 게 파일 쪼개는 방식입니다.

---

## 6. 파일 쪼개는 방식의 흐름

프론트는 파일을 서버가 정한 크기(5MB, `lab.chunked.chunk-size`)로 잘라서 조각마다 요청을 하나씩 보냅니다.

```
[브라우저]                                              [서버]
① POST /api/chunked/uploads                            start
   {"fileName":"a.bin","totalSize":12000000}            UUID 만들고 UploadSession 저장, 조각 개수 계산
   ← 201 {"uploadId":"…","chunkSize":5242880,"totalChunks":3}

② for (i = 0; i < totalChunks; i++)                    receiveChunk
     chunk = file.slice(i * chunkSize, …)               {i}.part.tmp 에 씀 (조각 크기 + 1 바이트까지만 읽음)
     PUT /api/chunked/uploads/{uploadId}/chunks/{i}     → 크기 확인 → {i}.part 로 이름 바꿈 → 받았다고 기록
     ← 200 {"index":i,"receivedCount":…,"totalChunks":3}

③ POST /api/chunked/uploads/{uploadId}/complete        complete
   ← 200 {"size":…,"sha256":"…", …}                     빠진 조각 확인 → 0.part, 1.part … 순서대로 붙임
                                                        (붙이면서 SHA-256 계산) → 조각 폴더 지움
```

디스크에는 이렇게 남습니다.

```
uploads/chunked/{uploadId}/0.part      받는 중인 조각들. 완료하면 폴더째 지움
uploads/chunked-{uploadId}.dat         합친 결과 (streaming-{id}.dat 와 같은 자리)
```

### 코드에서 볼 부분

- **조각 크기는 서버가 정함**: 클라이언트는 시작 응답으로 받은 크기대로 자르기만 합니다. 조각 개수도 서버가 계산합니다.
- **`file.slice()`는 파일을 읽지 않음**: "여기부터 여기까지"라는 표시만 만듭니다. 실제로 읽는 건 `fetch`로 보낼 때라서, 1GB 파일이어도 브라우저 메모리에는 조각 하나만 올라갑니다.
- **임시 파일 → 확정**: 조각은 `.tmp`에 먼저 쓰고 크기가 맞을 때만 이름을 바꿉니다. 망가진 조각이 다시 와도 전에 제대로 받은 같은 번호 조각을 덮어쓰지 않습니다.
- **같은 번호가 다시 와도 안전**: 덮어쓰고 한 번으로 셉니다. 끊겨서 다시 보내도 결과가 같습니다.
- **`uploadId` 형식 검사**: 임시 폴더 이름이 되기 때문에, 컨트롤러에서 UUID 형식이 아니면 400으로 막습니다.
- **오류 응답**: 잘못된 입력은 400, 모르는 `uploadId`는 404, 빠진 조각이 있는데 완료하거나 이미 끝난 업로드면 409입니다. 이유는 `{"message": "..."}`로 줍니다.

### 확인해 본 결과

2026-10-19에 50,000,000 bytes 랜덤 데이터를 화면 스크립트로 보냈습니다. 조각 10개(5MB × 9 + 2.8MB)가 갔고, 합친 파일의 SHA-256이 원본과 같았습니다. 완료 후 조각 폴더는 비었습니다.

### 아직 없는 것

- **이어 올리기**: 서버는 `missingChunks()`로 빠진 조각을 알지만, 그걸 화면에 알려 주는 API가 아직 없습니다.
- **병렬 전송**: 조각을 하나씩 순서대로 보냅니다. 같은 번호 조각이 동시에 오면 같은 `.tmp`를 같이 쓰게 됩니다.
- **세션 정리**: 세션이 메모리에만 있어서, 서버를 재시작하면 진행 중이던 업로드는 404가 됩니다. 완료하지 않고 버려진 조각 폴더를 지우는 일도 아직 없습니다.

---

## 7. 비교 실험: MultipartFile로 받으면

흔히 쓰는 `MultipartFile`로 1GB를 받으면 서버에서 무슨 일이 일어나는지 봅니다. 업로드 방식이 아니라 실험이라서 `multipart` 패키지에 컨트롤러와 필터만 있습니다.

    curl -F "file=@test-1g.bin" http://localhost:8098/api/multipart/transfer
    curl -F "file=@test-1g.bin" http://localhost:8098/api/multipart/bytes

- `/transfer`: 정석. `transferTo`로 저장합니다.
- `/bytes`: 하면 안 되는 것. `getBytes()`로 힙에 통째로 올립니다.
- 응답에 컨트롤러가 기다린 시간, 컨트롤러에 들어온 순간의 톰캣 임시 파일, 힙 사용량이 나옵니다.
- 스프링 부트 기본 제한(파일 1MB, 요청 10MB)으로는 1GB가 바로 거절되어서, `application.properties`에서 2GB로 늘려 뒀습니다.

### 확인해 본 결과

2026-10-19에 1GB 파일(`fsutil file createnew`)을 curl로 보냈습니다. 톰캣 임시 폴더는 C:, 저장 폴더는 D:입니다.

| | 컨트롤러에 들어오기까지 | 들어온 순간 톰캣 임시 폴더 | 컨트롤러 안 작업 | 힙 |
|---|---|---|---|---|
| `transferTo` | 31.2초 | `upload_….tmp` 1024MB 이미 있음 | 0.3초 | 12MB → 12MB |
| `getBytes()` (최대 힙 15GB) | 30.8초 | 같음 | 1.5초 | 12MB → 1044MB |
| `getBytes()` (`-Xmx512m`) | 약 31초 | 같음 | `OutOfMemoryError: Java heap space` | 500 응답 |

- 업로드 31초 동안 컨트롤러는 불리지 않습니다. 스프링이 본문을 끝까지 읽어 톰캣 임시 폴더에 다 쓴 다음에야 컨트롤러를 부릅니다.
- 메모리는 지켜지지만 디스크를 두 번 씁니다(임시 파일 → 진짜 위치). 임시 파일은 요청이 끝나면 지워집니다.
- `getBytes()`는 디스크에 잘 있던 파일을 힙으로 통째로 읽습니다. 힙이 작으면 그 요청이 `OutOfMemoryError`로 실패합니다.

### 스트리밍과 뭐가 다른가

둘 다 같은 톰캣이고, 둘 다 메모리에 1GB를 올리지 않습니다. 차이는 **본문을 누가, 언제 읽느냐**입니다.

```
[multipart]  요청 도착 → 스프링이 본문을 끝까지 읽어 임시 파일에 씀 → 컨트롤러 → 임시 파일을 진짜 위치로
[streaming]  요청 도착 → 컨트롤러 → 내 코드가 getInputStream()에서 8KB씩 읽어 진짜 위치에 씀
```

| | multipart | streaming |
|---|---|---|
| 본문을 읽는 주체 | 스프링 multipart 파서 | 내 코드 |
| 컨트롤러가 불리는 때 | 다 받은 뒤 | 헤더만 오면 바로 |
| 디스크 쓰기 | 두 번 | 한 번 |
| 임시 디스크 공간 | 업로드 크기만큼 (10명이면 10GB) | 필요 없음 |
| 받으면서 처리 | 불가능 | 가능 (해시 계산, 줄 단위 파싱, 다른 저장소로 넘기기) |

---

## 8. 받으면서 처리하기와 파일 형식

### 스트리밍은 담아 두지 않고 지나가는 데이터를 처리한다

스트리밍은 데이터가 지나가는 길목에 내 코드를 세워 두는 것입니다. 8KB가 들어오면 그걸로 할 일을 하고, 통을 비워 다음 8KB를 받습니다. 손에 들고 있는 건 언제나 지금 지나가는 8KB뿐입니다.

streaming lab도 디스크에 파일을 쓰지만, 그건 최종 목적지입니다. multipart처럼 다 받아 두고 나중에 처리하려는 중간 보관소가 아닙니다. 목적지가 꼭 디스크일 필요도 없습니다. 업체 목록 파일(TSV)이라면 받으면서 바로 DB에 넣을 수 있고, 그러면 1GB 파일이 서버 어디에도 남지 않습니다.

```java
BufferedReader reader = new BufferedReader(new InputStreamReader(request.getInputStream(), UTF_8));
String line;
while ((line = reader.readLine()) != null) {   // 한 줄 받으면
    String[] cols = line.split("\t");          // 바로 파싱해서
    batch.add(new Row(cols[0], cols[1]));      // 1000줄 모이면 DB에 넣고 비운다
    if (batch.size() == 1000) { repository.saveAll(batch); batch.clear(); }
}
```

대신 한 번 지나간 데이터는 다시 볼 수 없습니다. 이럴 때는 어딘가에 담아 둬야 합니다.

- **전체를 봐야 판단할 수 있을 때**: "파일 전체가 올바를 때만 넣는다"면, 중간에 틀린 줄이 나왔을 때 이미 넣은 줄을 되돌려야 합니다. 트랜잭션으로 묶거나, 일단 저장한 다음 검증합니다.
- **여러 번 읽어야 할 때**: 개수를 먼저 세고 처리하는 식이면 한 번에 끝낼 수 없습니다.
- **처리가 받는 속도보다 느릴 때**: 내 코드가 느리면 TCP 흐름 제어 때문에 업로드도 그만큼 느려집니다. 처리가 오래 걸리면 일단 파일로 받고 따로 처리하는 편이 낫습니다.

chunked는 반대입니다. 조각이 순서 없이, 재시도와 함께 올 수 있어서 다 모일 때까지 담아 둬야 합니다. 처리는 `complete` 이후에 시작합니다.

### 받는 중에 읽을 수 있는 형식, 없는 형식

텍스트(CSV, TSV)는 앞에서부터 순서대로 의미가 완성됩니다. 줄바꿈이 나오면 그 줄은 끝난 것이라 들어오는 대로 처리할 수 있습니다.

엑셀(`.xlsx`)은 ZIP 파일입니다.

```
a.xlsx (ZIP)
├── xl/workbook.xml
├── xl/worksheets/sheet1.xml    행 데이터. 셀에 글자 대신 번호가 있음: <c t="s"><v>42</v></c>
├── xl/sharedStrings.xml        42번이 실제로 어떤 글자인지
└── …
(파일 맨 끝) 목차: 어떤 파일이 ZIP 안 어디에 있는지
```

- ZIP의 목차가 파일 맨 끝에 있습니다. 앞에서부터 순서대로 읽는 방법(`ZipInputStream`)도 있어서 절대적인 벽은 아닙니다.
- 더 큰 문제는 행 데이터와 글자가 따로 있다는 점입니다. `sharedStrings.xml`이 시트보다 뒤에 오는 경우가 흔해서, 행을 받아도 글자를 알려면 끝까지 기다려야 합니다.
- 예전 형식인 `.xls`는 파일 안을 이리저리 건너뛰며 읽는 구조라 받는 중에는 더 안 됩니다. 다 받은 뒤에는 POI의 HSSF 이벤트 API(`HSSFEventFactory`)로 레코드를 하나씩 읽을 수 있지만, 코드가 번거롭고 쓰는 사람이 드뭅니다.

그래서 엑셀은 두 단계로 나눕니다. 스트리밍으로 디스크에 받은 다음(메모리 8KB), 저장된 파일을 행 단위로 읽습니다.

- POI의 `XSSFWorkbook`은 시트 전체를 객체로 만듭니다. 큰 파일이면 힙이 파일보다 훨씬 많이 필요합니다. `getBytes()`와 같은 함정입니다.
- POI 이벤트(SAX) 방식이나 스트리밍 리더 라이브러리를 쓰면 행 단위로 읽어 메모리를 적게 씁니다. 대신 대부분 다 받은 파일을 필요로 합니다.
- `sharedStrings`는 결국 메모리에 올라갑니다. 서로 다른 글자가 아주 많으면 이것만으로도 커질 수 있습니다.

| 형식 | 받는 중에 읽기 | 메모리 적게 읽기 |
|---|---|---|
| CSV / TSV | 가능 (한 줄씩) | 가능 |
| gzip으로 압축한 CSV | 가능 (`GZIPInputStream`으로 감쌈) | 가능 |
| 큰 JSON 배열 | 가능 (Jackson `JsonParser` 같은 스트리밍 파서) | 가능 |
| xlsx | 사실상 어려움 (다 받은 뒤에 읽기) | 가능 (SAX / 스트리밍 리더) |
| xls | 불가능 | 가능하지만 번거로움 (HSSF 이벤트 API) |

엑셀을 읽는 방법에 따라 힙이 얼마나 다른지는 11장에서 실제로 쟀습니다.

1GB 목록 파일이라면 업체에 CSV나 TSV로 달라고 하는 게 가장 확실합니다. 엑셀은 시트 하나에 약 104만 행까지라는 제한도 있습니다.

---

## 9. 받으면서 읽기: text와 CSV

8장의 "받으면서 처리하기"를 실제로 돌려 봅니다. 파일을 저장하지 않고, 들어오는 대로 한 줄(한 행)씩 읽어 서버 로그에 남긴 뒤 버립니다. 실제 서비스라면 로그를 찍는 자리에서 DB에 넣으면 됩니다.

    curl -T list.csv -H "Content-Type: application/octet-stream" -H "X-File-Name: list.csv" http://localhost:8098/api/streaming/read/csv
    curl -T log.txt  -H "Content-Type: application/octet-stream" -H "X-File-Name: log.txt"  http://localhost:8098/api/streaming/read/text

스트리밍 화면의 "4. 받으면서 읽기"에서 파일을 고르고 "text로 읽기", "csv로 읽기"를 눌러도 됩니다. 보내는 방법은 업로드와 똑같고 주소만 다릅니다.

```
[StreamReadController]  request.getInputStream()
     ▼
[StreamReadService]     CountingInputStream     지금까지 받은 바이트 수를 셈 (로그용)
                        InputStreamReader(UTF-8) 바이트 → 글자
                        text: BufferedReader.readLine()   한 줄씩
                        csv : CsvRowReader.next()         한 행씩
                        처음 5줄은 내용을, 그다음은 10만 줄마다 진행 상황을 로그로 찍음
```

### 로그는 이렇게 찍힙니다

2026-10-19에 힙을 64MB로 제한한 서버(`java -Xmx64m -jar ...`)에 60MB짜리 CSV(100만 행)를 초당 20MB로 보냈습니다.

```
14:49:18.972 INFO [csv] 시작: list.csv
14:49:18.974 INFO [csv] 헤더 3칸: [path, size, memo]
14:49:18.977 INFO [csv] 1번째 행 (지금까지 받은 8,192 bytes): {path=/img/a.jpg, size=100, memo=서울, 강남}
14:49:18.977 INFO [csv] 2번째 행 (지금까지 받은 8,192 bytes): {path=/img/b.jpg, size=200, memo=말 "인용" 끝}
14:49:18.977 INFO [csv] 3번째 행 (지금까지 받은 8,192 bytes): {path=/img/c.jpg, size=300, memo=첫 줄\n둘째 줄}
14:49:18.977 WARN [csv] 4번째 행은 칸이 2개입니다 (헤더는 3개): [/img/d.jpg, 400]
14:49:18.977 INFO [csv] 5번째 행 (지금까지 받은 8,192 bytes): {path=/cdn/2026/10/00000000/photo_0.jpg, size=0, memo=메모0}
14:49:19.465 INFO [csv] 100,000행 읽음, 받은 5.6 MB, 0.5초, 힙 28 MB
14:49:19.841 INFO [csv] 200,000행 읽음, 받은 11.4 MB, 0.9초, 힙 18 MB
...
14:49:21.859 INFO [csv] 1,000,000행 읽음, 받은 57.8 MB, 2.9초, 힙 29 MB
14:49:21.863 INFO [csv] 끝: 1,000,004행, 칸 수가 틀린 행 1개, 받은 57.8 MB, 2.9초, 힙 29 MB
```

- **첫 행은 8KB만 받았을 때 이미 처리됩니다.** 파일 전체를 기다리지 않습니다.
- **"받은 MB"가 보내는 속도(초당 20MB)대로 늘어납니다.** 업로드가 끝나기 전부터 읽고 있다는 뜻입니다.
- **힙이 20~30MB 사이를 오갑니다.** 60MB 파일을 64MB 힙에서 읽었습니다. text(80MB, 100만 줄)도 최대 32MB였습니다.
- 힙을 제한하지 않으면 힙 사용량이 300MB까지 올라갔다가 떨어집니다. 읽고 버린 줄이 GC로 아직 안 치워졌을 뿐이고 붙잡고 있는 메모리가 아닙니다. 그래서 힙을 작게 제한해서 확인했습니다.
- 형식이 틀리면 400입니다: `{"message":"2번째 줄에서 연 따옴표가 닫히지 않은 채 파일이 끝났습니다"}`

### 코드에서 볼 부분

- **CSV를 `readLine()` + `split(",")`로 읽지 않음**: 위 로그의 1~3번째 행처럼 따옴표 안의 쉼표, 따옴표 글자를 뜻하는 `""`, 따옴표 안의 줄바꿈이 있으면 틀립니다. 특히 따옴표 안의 줄바꿈은 `readLine()`이 행 하나를 둘로 쪼갭니다. `CsvRowReader`는 글자를 하나씩 보며 따옴표 안인지 밖인지 기억합니다.
- **한 행이 너무 길면 끊음**: 줄바꿈 없는 1GB가 오면 한 행이 1GB가 됩니다. `CsvRowReader`는 100만 글자를 넘으면 예외를 던집니다. text 쪽 `readLine()`에는 이 제한이 없어서, 줄바꿈 없는 큰 파일이 오면 힙에 그대로 쌓입니다.
- **BOM**: 엑셀이 UTF-8로 저장한 CSV는 맨 앞에 `\uFEFF`(BOM)가 붙어서, 그대로 두면 첫 헤더 이름이 `\uFEFFpath`가 됩니다. 떼어 냅니다.
- **인코딩은 UTF-8 고정**: 한국어 엑셀의 기본 CSV 저장(CP949)으로 만든 파일은 한글이 깨집니다.
- **로그를 줄마다 찍지 않음**: 1GB면 몇백만 줄이라 로그가 넘칩니다. 처음 5줄만 내용을, 그다음은 10만 줄마다 진행 상황만 찍습니다.
- **테스트**: `CsvRowReaderTest`가 따옴표, 줄바꿈, BOM, 닫히지 않은 따옴표, 너무 긴 행을 확인합니다. `StreamReadServiceTest`는 100만 행 CSV를 배열 없이 그때그때 만들어 흘려 보냅니다.

---

## 10. 받으면서 DB에 넣기 (H2)

9장에서 로그를 찍던 자리에서 진짜로 DB에 넣습니다. 파일은 디스크 어디에도 저장하지 않습니다. CSV 헤더에 `path`, `size` 칸이 있어야 합니다. 순서는 상관없고 다른 칸은 무시합니다.

    curl -X POST -T docs/test-data/list.csv -H "Content-Type: application/octet-stream" -H "X-File-Name: list.csv" http://localhost:8098/api/streaming/imports
    curl http://localhost:8098/api/streaming/imports      넣기 기록 목록 (최신순)

스트리밍 화면의 "5. CSV를 DB에 넣기"에서도 됩니다. 넣은 행은 H2 콘솔에서 `SELECT * FROM cdn_file`로 봅니다.

```
POST /api/streaming/imports
  [CsvImportController]  request.getInputStream()
  [CsvImportService]
     1. csv_import 에 IMPORTING 으로 기록
     2. CsvRowReader 로 한 행씩 읽음 → CdnFileColumns 가 path, size 검사 → 틀린 행은 건너뛰고 셈
     3. 1000행이 모이면 cdn_file 에 batchUpdate. DB 연결은 이때만 잠깐 빌림
     4. 끝나면 COMPLETED
        실패하면(형식 오류, 업로드 끊김, DB 오류) 그 import_id 로 넣은 행을 지우고 FAILED
```

| 테이블 | 한 줄이 뜻하는 것 |
|---|---|
| `csv_import` | CSV 한 번 넣기. 상태, 넣은 행 수, 건너뛴 행 수, 실패 이유, 시작/끝 시각 |
| `cdn_file` | CSV 한 행. `import_id`로 어느 CSV에서 왔는지 묶음 |

### 트랜잭션 하나로 묶지 않는 이유

- **묶으면**: 업로드가 끝날 때까지(느린 사용자는 몇 분) DB 연결 하나를 붙잡습니다. HikariCP 기본 풀은 10개라서 동시 업로드 10개면 다른 API가 DB를 못 씁니다. 실패하면 롤백 한 번으로 깔끔하게 끝나는 건 장점입니다.
- **안 묶으면(이 lab)**: 1000행마다 연결을 빌렸다가 바로 돌려줍니다. 대신 중간에 실패하면 이미 들어간 행이 남습니다. 그래서 `import_id`로 찾아 지우고(보상), 기록을 FAILED로 남깁니다. 지우는 동안에는 일부 행이 잠깐 보일 수 있습니다.

### 확인해 본 결과

2026-10-19에 힙을 64MB로 제한한 서버에 `docs/test-data/list.csv`(60MB, 100만 4행)를 초당 20MB로 보냈습니다.

```
15:28:03.822 INFO [import fc1978d2] 시작: list.csv
15:28:03.823 INFO [import fc1978d2] 헤더 확인: path=0번째 칸, size=1번째 칸 (0부터), 전체 3칸
15:28:03.825 WARN [import fc1978d2] 4번째 행 건너뜀: 칸이 2개입니다 (헤더는 3개) [/img/d.jpg, 400]
15:28:03.868 INFO [import fc1978d2] 1,000행 넣음, 받은 0.1 MB, 0.0초
15:28:04.431 INFO [import fc1978d2] 100,000행 넣음, 받은 5.6 MB, 0.6초
...
15:28:07.569 INFO [import fc1978d2] 끝: 1,000,003행 넣음, 건너뛴 행 1개, 받은 57.8 MB, 3.7초
```

| 경우 | 결과 |
|---|---|
| 정상 | 201, COMPLETED, 1,000,003행 넣음, 건너뛴 행 1개. 3.7초 |
| 업로드 도중 curl을 2초 만에 끊음 | FAILED. 그때까지 넣은 176,000행을 지움. 실패 이유는 `java.io.EOFException` |
| 헤더에 `size`가 없음 (`docs/test-data/nosize.csv`) | 400 `{"message":"헤더에 path, size 칸이 있어야 합니다: [path, bytes]"}`, FAILED |

- 0.1MB만 받았을 때 첫 1000행이 이미 DB에 들어갔습니다.
- 끊긴 업로드는 curl이 끊고 2초쯤 뒤에 서버가 알아챘습니다. 톰캣이 다음 `read()`에서 연결이 닫힌 걸 보고 예외를 던질 때입니다.

### JPA 대신 JdbcTemplate을 쓴 이유

JPA로 못 하는 건 아니지만, 대량 입력에는 기본 동작이 불리합니다. 아래는 실험한 게 아니라 일반적인 동작입니다.

- **id가 `IDENTITY`(AUTO_INCREMENT)면 묶어 보내지 못합니다.** Hibernate가 저장하자마자 id를 알아야 해서 INSERT를 하나씩 바로 실행합니다. `saveAll()`로 100만 개를 넘겨도 100만 번 왕복합니다.
- **영속성 컨텍스트에 엔티티가 쌓입니다.** 한 트랜잭션에서 저장한 엔티티를 1차 캐시가 모두 들고 있어서, 스트리밍으로 아낀 메모리가 다시 찹니다.
- **JPA로 하려면** id를 `SEQUENCE`로 바꾸고, `hibernate.jdbc.batch_size`와 `order_inserts`를 켜고, 1000개마다 `flush()`와 `clear()`를 부르거나 `StatelessSession`을 씁니다. JPA의 편의 기능을 거의 다 끄는 셈입니다.

보통은 일반 화면의 저장과 조회는 JPA로, 대량 입력만 JdbcTemplate으로 나눠 씁니다. 더 크면 DB 자체 기능(MySQL `LOAD DATA`, PostgreSQL `COPY`)을 씁니다. MySQL은 JDBC URL에 `rewriteBatchedStatements=true`가 있어야 batch가 실제로 여러 행 INSERT로 묶입니다.

### 코드에서 볼 부분

- **`JdbcCdnFileRowAdapter.saveAll()`**: `batchUpdate`로 INSERT 1000개를 한 번에 보냅니다.
- **`CsvImport`**: 상태가 바뀔 때마다 새 값을 만듭니다. 이미 끝난 import를 다시 끝내려 하면 예외입니다.
- **`CdnFileColumns`**: 틀린 행이면 이유를 담은 `IllegalArgumentException`을 던지고, 서비스는 그걸 세고 건너뜁니다. 처음 5개만 로그에 남깁니다.
- **시각을 밀리초로 자름**: Windows의 `LocalDateTime.now()`는 100나노초 단위인데 H2 `TIMESTAMP`는 마이크로초까지만 저장합니다. 그대로 두면 응답으로 돌려준 값과 DB에서 다시 읽은 값이 다릅니다. 테스트가 이걸 잡았습니다.
- **테스트**: `CsvImportServiceTest`는 `@JdbcTest`로 메모리 H2에 진짜 JDBC 어댑터를 끼워 돌립니다. 1000행 단위를 넘는 경우, 1000행이 들어간 뒤 끊긴 경우(넣은 행이 0이 되는지), 헤더가 틀린 경우를 확인합니다. `CdnFileColumnsTest`는 행 검사를 확인합니다.

### 아직 없는 것

- **동시 업로드 제한**: 여러 명이 동시에 넣으면 각자 스레드와 DB 연결을 번갈아 씁니다. 동시에 몇 개까지 받을지 제한하는 코드는 없습니다.
- **같은 파일 두 번 넣기**: 같은 CSV를 두 번 올리면 행이 두 벌 들어갑니다. `import_id`가 달라서 구분은 됩니다.
- **실패 이유 메시지**: 업로드가 끊기면 실패 이유가 `java.io.EOFException`으로만 남습니다.

---

## 11. 비교 실험: 엑셀을 읽는 세 가지 방법과 힙

multipart로 받은 엑셀은 이미 톰캣 임시 폴더(디스크)에 있습니다(7장). 내용을 읽으려고 파일 전체를 메모리에 올릴 필요는 없고, **어떻게 읽느냐**에 따라 힙이 크게 달라집니다.

    curl -F "file=@docs/test-data/list.xlsx" http://localhost:8098/api/multipart/excel/workbook-stream
    curl -F "file=@docs/test-data/list.xlsx" http://localhost:8098/api/multipart/excel/workbook-file
    curl -F "file=@docs/test-data/list.xlsx" http://localhost:8098/api/multipart/excel/sax

| 주소 | 읽는 방법 | ZIP 압축 해제 | 셀 객체 |
|---|---|---|---|
| `/workbook-stream` | `new XSSFWorkbook(file.getInputStream())` | 전부 메모리에 | 전부 메모리에 |
| `/workbook-file` | 내 임시 파일로 옮긴 뒤 `new XSSFWorkbook(OPCPackage.open(file, READ))` | 필요한 부분만 | 전부 메모리에 |
| `/sax` | 내 임시 파일로 옮긴 뒤 `XSSFReader` + `XSSFSheetXMLHandler` (SAX) | 필요한 부분만 | 지금 읽는 셀만 |

- `file.getInputStream()` 자체는 임시 파일을 읽는 통로라 메모리를 거의 안 씁니다. InputStream은 앞에서부터 한 번만 읽을 수 있는데 ZIP은 이리저리 오가며 읽어야 해서, **POI가 ZIP 안의 파일들을 압축을 푼 채 전부 메모리에 담아 둡니다.** 이게 첫 번째 겹입니다.
- `XSSFWorkbook`은 File로 열어도 **모든 행과 셀을 객체로 만들어 들고 있습니다.** `getRow(5).getCell(3)`처럼 아무 데나 꺼내 볼 수 있게 하려는 것입니다. 이게 두 번째 겹이고 훨씬 큽니다.
- SAX는 시트 XML을 앞에서부터 흘려 읽으면서 셀이 하나 지나갈 때마다 내 코드를 부릅니다. 셀을 쌓아 두지 않습니다. 대신 글자 사전(`sharedStrings.xml`)은 메모리에 올라갑니다.
- `MultipartFile`은 톰캣 임시 파일 경로를 알려 주지 않아서, File로 열려면 `transferTo`로 내 임시 파일에 옮깁니다.
- excel-streaming-reader, FastExcel 같은 라이브러리도 SAX 방식으로 읽습니다. 이 실험은 라이브러리를 더 넣지 않고 POI에 있는 SAX를 씁니다.

세 방법 모두 첫 시트의 모든 셀을 글자로 바꿔 읽습니다(`DataFormatter`). 하는 일은 같고 읽는 방법만 다릅니다. 응답의 힙 값은 이렇습니다.

- `heapRetainedMb`: 다 읽고 workbook(또는 글자 사전)을 아직 쥐고 있을 때, GC를 한 다음 잰 값. **진짜로 붙잡고 있는 양**입니다.
- `heapPeakMb`: 읽는 동안 10ms마다 잰 값 중 가장 큰 값. 아직 안 치운 쓰레기도 들어 있어서 들쭉날쭉합니다.

### 실험 파일

`docs/test-data/list.xlsx`: 헤더 `path,size,memo,type` + 20만 행, 셀 80만 개. 파일은 5.9MB이고, 압축을 풀면 시트 XML 34MB와 글자 사전 16MB입니다. 만드는 방법은 `docs/test-data/README.md`에 있습니다.

처음에 파이썬 openpyxl로 만들었더니 글자가 셀 안에 바로 들어가서(`inlineStr`) `sharedStrings.xml`이 없었습니다. 엑셀이 만든 파일과 달라서 POI `SXSSFWorkbook`(글자 사전 사용)으로 만들었습니다.

### 확인해 본 결과

2026-10-19에 쟀습니다.

힙 제한 없음 (최대 15GB):

| 방법 | 붙잡고 있는 힙 | 읽는 동안 최대 | 걸린 시간 |
|---|---|---|---|
| `XSSFWorkbook(InputStream)` | **907MB** | 1462MB | 5.9초 |
| `XSSFWorkbook(File)` | **851MB** | 1781MB | 4.1초 |
| SAX + File | **48MB** | 369MB | 1.6초 |

- 5.9MB 엑셀을 `XSSFWorkbook`으로 읽으면 힙을 약 900MB(파일의 150배) 붙잡습니다.
- 두 `XSSFWorkbook`의 차이 56MB는 압축을 푼 크기(34MB + 16MB)와 거의 같습니다. InputStream으로 열 때만 생기는 첫 번째 겹입니다.
- SAX의 48MB는 서버 기본 사용량(약 17MB)에 글자 사전이 더해진 양입니다.

힙을 줄여서 (방법마다 서버를 새로 띄움):

| 방법 | `-Xmx1g` | `-Xmx256m` | `-Xmx64m` |
|---|---|---|---|
| `XSSFWorkbook(InputStream)` | 성공 (895MB) | OOM, **그 뒤 서버 먹통** | OOM, 서버 살아 있음 |
| `XSSFWorkbook(File)` | 성공 (846MB) | OOM, **그 뒤 서버 먹통** | OOM, **그 뒤 서버 먹통** |
| SAX + File | 성공 (49MB) | 성공 | 성공 |

"서버 먹통"은 OOM 응답이 온 뒤 다른 API(`GET /api/streaming/uploads`)를 불렀을 때 10초 안에 응답이 없었다는 뜻입니다.

### OOM이 서버 전체를 멈출 수 있다

7장 `getBytes()` 실험에서는 OOM이 나도 그 요청만 500으로 실패하고 서버는 살아 있었습니다. 이번에는 OOM 뒤에 서버가 요청을 받지 못했습니다. 서버 로그를 보면 톰캣이 연결을 받아 넘기는 스레드가 죽어 있었습니다.

```
Exception: java.lang.OutOfMemoryError thrown from the UncaughtExceptionHandler in thread "http-nio-18098-Poller"
ERROR ... o.a.coyote.http11.Http11NioProtocol : Error processing async timeouts
```

메모리가 차는 방식이 다릅니다.

- **큰 배열 하나를 한 번에 요청하다 실패**(`getBytes()`, 64MB에서의 `XSSFWorkbook(InputStream)`): 그 요청 스레드만 OOM을 맞습니다. 서버는 살아남습니다.
- **작은 객체들이 힙을 조금씩 채움**(`XSSFWorkbook`이 셀 객체를 만드는 중): 힙이 꽉 찬 순간 메모리를 조금이라도 달라고 한 다른 스레드도 OOM을 맞습니다. 톰캣 내부 스레드가 죽으면 프로세스는 살아 있는데 요청을 못 받는 상태가 됩니다.

그래서 OOM이 나면 그 요청만 실패할 거라고 기대하면 안 됩니다. 운영에서는 `-XX:+ExitOnOutOfMemoryError`로 OOM이 나면 프로세스를 끝내고 다시 띄우게 하는 경우가 많습니다. 이 옵션은 일반적인 권장 사항이고 이 실험에서 돌려 보지는 않았습니다.

### 정리

- 큰 엑셀은 `XSSFWorkbook`으로 읽지 않습니다. SAX나 스트리밍 리더로 한 행씩 읽고, 10장처럼 1000행씩 DB에 넣습니다.
- 열 때는 InputStream이 아니라 File로 엽니다.
- 글자 사전은 메모리에 올라갑니다. 서로 다른 글자가 아주 많은 파일이면 이것도 커집니다.
- 압축 폭탄(작은 xlsx가 풀면 몇 GB가 되는 파일)은 POI가 기본으로 막습니다(`ZipSecureFile`). 이 설정을 끄지 않습니다.
