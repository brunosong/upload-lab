package com.example.uploadlab.streaming.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 읽다가 형식이 틀린 걸 만나면(닫히지 않은 따옴표, 너무 긴 행, 빈 파일, 필요한 칸이 없는 헤더) 500 대신 400으로 알려준다.
 */
@RestControllerAdvice(assignableTypes = {StreamReadController.class, CsvImportController.class})
public class StreamReadExceptionHandler {

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badFormat(IllegalArgumentException e) {
        return new ErrorResponse(e.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
