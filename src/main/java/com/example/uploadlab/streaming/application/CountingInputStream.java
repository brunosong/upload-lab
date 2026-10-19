package com.example.uploadlab.streaming.application;

import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

/**
 * 네트워크에서 지금까지 받은 바이트 수를 센다. 로그에 "받은 12.3 MB"를 찍는 용도.
 * StreamReadService, CsvImportService 가 같이 쓴다.
 */
class CountingInputStream extends FilterInputStream {

    long count;

    CountingInputStream(InputStream in) {
        super(in);
    }

    @Override
    public int read() throws IOException {
        int b = super.read();
        if (b != -1) count++;
        return b;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n > 0) count += n;
        return n;
    }
}
