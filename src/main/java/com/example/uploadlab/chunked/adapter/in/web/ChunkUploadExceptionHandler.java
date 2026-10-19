package com.example.uploadlab.chunked.adapter.in.web;

import com.example.uploadlab.chunked.application.UploadSessionNotFoundException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 도메인이 던진 예외를 HTTP 상태로 바꾼다. 화면은 {"message": "..."} 로 이유를 받는다.
 * basePackageClasses로 범위를 좁혀서 streaming 쪽 응답에는 영향이 없다.
 */
@RestControllerAdvice(basePackageClasses = ChunkUploadController.class)
public class ChunkUploadExceptionHandler {

    /** 잘못된 입력: 조각 번호 범위 밖, 크기 불일치, 잘못된 uploadId */
    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ErrorResponse badRequest(IllegalArgumentException e) {
        return new ErrorResponse(e.getMessage());
    }

    /** 지금 상태에서 할 수 없는 일: 이미 완료됨, 조각이 빠졌는데 완료 요청 */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ErrorResponse conflict(IllegalStateException e) {
        return new ErrorResponse(e.getMessage());
    }

    /** 모르는 uploadId. 서버를 재시작해서 메모리 세션이 사라진 경우도 여기에 해당한다 */
    @ExceptionHandler(UploadSessionNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ErrorResponse notFound(UploadSessionNotFoundException e) {
        return new ErrorResponse(e.getMessage());
    }

    public record ErrorResponse(String message) {
    }
}
