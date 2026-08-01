package com.enterprise.api;

import jakarta.servlet.Filter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
@Order(1)
public class RequestSizeFilter implements Filter {

    private static final int MAX_REQUEST_SIZE = 10 * 1024; // 10 KB

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {
        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Only check POST/PUT requests with JSON content
        String method = httpRequest.getMethod();
        String contentType = httpRequest.getContentType();
        if (("POST".equals(method) || "PUT".equals(method)) &&
            contentType != null && contentType.contains("application/json")) {
            int contentLength = httpRequest.getContentLength();
            if (contentLength > MAX_REQUEST_SIZE) {
                httpResponse.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
                httpResponse.setContentType("application/json");
                httpResponse.getWriter().write("{\"code\":\"REQ-002\",\"message\":\"Request body too large (max 10KB)\"}");
                return;
            }
        }
        chain.doFilter(request, response);
    }
}