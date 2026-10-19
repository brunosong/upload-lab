# 테스트 데이터를 만든다. 이 폴더에서 실행한다:  python make_test_data.py
#
#   list.csv  약 60MB, 100만 4행. 엑셀식 UTF-8(BOM 있음), 줄바꿈 \r\n
#             앞의 4행에 일부러 까다로운 경우를 넣었다 (따옴표 안 쉼표, "", 따옴표 안 줄바꿈, 칸 모자람)
#   log.txt   약 80MB, 100만 줄. BOM 없음, 줄바꿈 \n
import io

with io.open('list.csv', 'w', encoding='utf-8-sig', newline='') as f:
    f.write('path,size,memo\r\n')
    f.write('/img/a.jpg,100,"서울, 강남"\r\n')
    f.write('/img/b.jpg,200,"말 ""인용"" 끝"\r\n')
    f.write('/img/c.jpg,300,"첫 줄\r\n둘째 줄"\r\n')
    f.write('/img/d.jpg,400\r\n')
    for i in range(1_000_000):
        f.write(f'/cdn/2026/10/{i:08d}/photo_{i}.jpg,{i * 37 % 9999999},메모{i}\r\n')

with io.open('log.txt', 'w', encoding='utf-8', newline='') as f:
    for i in range(1_000_000):
        f.write(f'2026-10-19 10:{i // 60 % 60:02d}:{i % 60:02d} INFO 요청 {i}번 처리 /cdn/2026/10/{i:08d}/photo.jpg\n')
