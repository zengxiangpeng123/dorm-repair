package com.zxp.dormrepair.auth;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** 为每个请求生成关联 ID，记录状态码和耗时，不记录请求体、令牌或个人信息。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestObservabilityFilter extends OncePerRequestFilter {
    public static final String REQUEST_ID = "requestId";
    private static final Logger log = LoggerFactory.getLogger(RequestObservabilityFilter.class);

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String incoming = req.getHeader("X-Request-Id");
        String requestId = incoming == null || incoming.isBlank() || incoming.length() > 100
                ? UUID.randomUUID().toString() : incoming;
        long started = System.nanoTime();
        MDC.put(REQUEST_ID, requestId);
        res.setHeader("X-Request-Id", requestId);
        try {
            chain.doFilter(req, res);
        } finally {
            long ms = (System.nanoTime() - started) / 1_000_000;
            log.info("http request method={} path={} status={} durationMs={} requestId={}",
                    req.getMethod(), req.getRequestURI(), res.getStatus(), ms, requestId);
            MDC.remove(REQUEST_ID);
        }
    }
}
