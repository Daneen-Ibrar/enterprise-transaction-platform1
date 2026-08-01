package com.enterprise.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Enumeration;

@Component
@Order(1)
public class DebugFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(DebugFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        String method = request.getMethod();

        // Only log admin/invoice endpoints
        if (uri.startsWith("/admin/invoices")) {
            log.info("📨 ===== REQUEST ===== ");
            log.info("   Method: {}", method);
            log.info("   URI: {}", uri);
            log.info("   Query String: {}", request.getQueryString());

            // Log all parameters
            Enumeration<String> paramNames = request.getParameterNames();
            if (paramNames.hasMoreElements()) {
                log.info("   Parameters:");
                while (paramNames.hasMoreElements()) {
                    String name = paramNames.nextElement();
                    String value = request.getParameter(name);
                    log.info("      {} = {}", name, value);
                }
            }

            // Log all headers
            Enumeration<String> headerNames = request.getHeaderNames();
            if (headerNames.hasMoreElements()) {
                log.info("   Headers:");
                while (headerNames.hasMoreElements()) {
                    String name = headerNames.nextElement();
                    String value = request.getHeader(name);
                    log.info("      {} = {}", name, value);
                }
            }

            log.info("   Remote IP: {}", request.getRemoteAddr());
            log.info("================================");
        }

        chain.doFilter(request, response);
    }
}