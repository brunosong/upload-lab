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
    │   └── UploadedFile.java                    업로드 결과 한 건. 스프링을 모르는 순수 값
    ├── application/
    │   ├── port/in/StreamUploadUseCase.java     바깥이 이 기능에 시키는 일
    │   ├── port/out/FileStoragePort.java        어디에 저장할지 (구현은 어댑터)
    │   ├── port/out/UploadRecordPort.java       어디에 기록할지 (구현은 어댑터)
    │   └── StreamUploadService.java             흐름만 담음. 디스크, 메모리를 직접 모름
    └── adapter/
        ├── in/web/StreamUploadController.java   HTTP 요청을 유스케이스 호출로 바꿈
        ├── out/storage/LocalDiskStorageAdapter.java     8KB씩 받아 디스크에 씀
        ├── out/record/InMemoryUploadRecordAdapter.java  기록을 메모리에 둠 (lab.record.type=memory)
        └── out/record/FileUploadRecordAdapter.java      기록을 records.jsonl 에 둠 (lab.record.type=file)

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
    └── ArrivalTimeFilter.java                   요청 도착 시각을 남겨 컨트롤러가 기다린 시간을 잼

resources/static/
├── index.html                메인. 방식 고르기
├── streaming/index.html      스트리밍 방식 화면
└── chunked/index.html        파일 쪼개는 방식 화면
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
