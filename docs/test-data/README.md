# 테스트 데이터

README 9장(받으면서 읽기), 10장(CSV를 DB에 넣기)을 확인할 때 쓴 파일입니다.

| 파일 | 크기 | 내용 | git |
|---|---|---|---|
| `list.csv` | 약 60MB | 헤더 `path,size,memo` + 100만 4행. 엑셀식 UTF-8(BOM 있음), 줄바꿈 `\r\n` | 올리지 않음 |
| `log.txt` | 약 80MB | 로그 형식 100만 줄. 줄바꿈 `\n` | 올리지 않음 |
| `nosize.csv` | 20 bytes | 헤더에 `size`가 없는 CSV. DB에 넣기가 400으로 거절되는지 볼 때 | 올림 |
| `make_test_data.py` | | `list.csv`, `log.txt`를 만드는 스크립트 | 올림 |
| `list.xlsx` | 약 5.9MB | 엑셀 힙 비교 실험(README 11장)용. 헤더 `path,size,memo,type` + 20만 행. 글자는 엑셀처럼 `sharedStrings.xml`에 모음 (압축 풀면 시트 34MB, 글자 사전 16MB) | 올리지 않음 |

큰 파일은 `.gitignore`에 넣었습니다. 없으면 다시 만듭니다. `list.csv`, `log.txt`는 바이트 단위로 똑같이 나옵니다.

    cd docs\test-data
    python make_test_data.py

`list.xlsx`는 POI로 만듭니다 (프로젝트 폴더에서). 파이썬 openpyxl은 글자를 셀 안에 바로 써서(`inlineStr`) 엑셀이 만든 파일과 달라지기 때문입니다.

    mvnw -q test-compile exec:java -Dexec.classpathScope=test -Dexec.mainClass=com.example.uploadlab.multipart.MakeTestExcel -Dexec.args="docs/test-data/list.xlsx 200000"

`list.csv` 앞부분에는 일부러 까다로운 행을 넣었습니다.

```
path,size,memo
/img/a.jpg,100,"서울, 강남"          ← 따옴표 안의 쉼표
/img/b.jpg,200,"말 ""인용"" 끝"      ← 따옴표 안의 "" (따옴표 글자 하나)
/img/c.jpg,300,"첫 줄                ← 따옴표 안의 줄바꿈 (한 행이 두 줄)
둘째 줄"
/img/d.jpg,400                       ← 칸이 모자람. 건너뛴 행으로 세야 함
/cdn/2026/10/00000000/photo_0.jpg,0,메모0
...
```

그래서 결과는 이렇게 나와야 합니다.

- 받으면서 읽기(`/api/streaming/read/csv`): 1,000,004행, 칸 수가 틀린 행 1개
- DB에 넣기(`/api/streaming/imports`): 1,000,003행 넣음, 건너뛴 행 1개

보내기 (프로젝트 폴더에서):

    curl -X POST -T docs/test-data/list.csv -H "Content-Type: application/octet-stream" -H "X-File-Name: list.csv" http://localhost:8098/api/streaming/imports
    curl -T docs/test-data/log.txt -H "Content-Type: application/octet-stream" -H "X-File-Name: log.txt" http://localhost:8098/api/streaming/read/text
