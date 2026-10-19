package com.example.uploadlab.multipart;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 요청이 도착한 시각을 남긴다.
 *
 * 필터는 헤더만 읽은 상태에서 불린다. 본문(파일)은 아직 안 받았다.
 * multipart는 이 다음, DispatcherServlet이 본문을 끝까지 풀고 나서야 컨트롤러를 부른다.
 * 그래서 "컨트롤러에 들어온 시각 - 이 시각"이 컨트롤러가 기다린 시간이다.
 */
@Component
public class ArrivalTimeFilter extends OncePerRequestFilter {

    static final String ARRIVED_AT = "multipart.arrivedAt";

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/multipart/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        request.setAttribute(ARRIVED_AT, System.currentTimeMillis());
        chain.doFilter(request, response);
    }
}
