package com.zxp.dormrepair.auth;

import tools.jackson.databind.json.JsonMapper;
import com.zxp.dormrepair.common.ApiResponses;
import com.zxp.dormrepair.common.BizException;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.domain.IdempotencyRecord;
import com.zxp.dormrepair.service.IdempotencyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingResponseWrapper;

/** 关键写命令的幂等入口。只缓存成功响应；失败不缓存，修正后可用同一个 key 重试。 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public class IdempotencyFilter extends OncePerRequestFilter {

    public static final String HEADER = "Idempotency-Key";
    private static final Set<String> WRITE_METHODS = Set.of("POST", "PUT", "PATCH", "DELETE");

    private final IdempotencyService idempotency;

    public IdempotencyFilter(IdempotencyService idempotency) {
        this.idempotency = idempotency;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (!path.startsWith("/api/v1/")) return true;
        if (!WRITE_METHODS.contains(req.getMethod())) return true;
        if (path.equals("/api/v1/auth/login") || path.equals("/api/v1/auth/logout")) return true;
        if (path.startsWith("/api/v1/files")) return true;
        return req.getHeader(HEADER) == null || req.getHeader(HEADER).isBlank();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        Object attr = request.getAttribute(AuthFilter.ATTR);
        if (!(attr instanceof Actor actor)) {
            chain.doFilter(request, response);
            return;
        }
        byte[] bodyBytes = request.getInputStream().readAllBytes();
        HttpServletRequest replayable = new BodyReplayRequest(request, bodyBytes);
        ContentCachingResponseWrapper res = new ContentCachingResponseWrapper(response);
        String key = request.getHeader(HEADER).trim();
        String path = request.getRequestURI();
        String method = request.getMethod();
        IdempotencyRecord owner = null;
        try {
            String requestHash = IdempotencyService.canonicalHash(method, path,
                    new String(bodyBytes, StandardCharsets.UTF_8));
            IdempotencyService.Claim claim = idempotency.begin(actor, method, path, key, requestHash);
            if (claim.replay() != null) {
                res.setStatus(claim.replay().responseStatus());
                res.setContentType("application/json;charset=UTF-8");
                res.getWriter().write(claim.replay().responseBody());
                return;
            }
            owner = claim.owner();
            chain.doFilter(replayable, res);
            if (res.getStatus() >= 200 && res.getStatus() < 300) {
                String responseBody = new String(res.getContentAsByteArray(), StandardCharsets.UTF_8);
                idempotency.complete(owner, res.getStatus(), responseBody);
            } else {
                idempotency.release(owner);
            }
        } catch (BizException e) {
            idempotency.release(owner);
            res.resetBuffer();
            res.setStatus(e.status().value());
            res.setContentType("application/json;charset=UTF-8");
            res.getWriter().write(JsonMapper.builder().build()
                    .writeValueAsString(ApiResponses.error(e.code(), e.getMessage(), e.extra())));
        } catch (Exception e) {
            idempotency.release(owner);
            throw e;
        } finally {
            res.copyBodyToResponse();
        }
    }

    /** 已读取请求体后重新提供输入流，避免 Controller 收到空 JSON。 */
    private static final class BodyReplayRequest extends HttpServletRequestWrapper {
        private final byte[] body;

        private BodyReplayRequest(HttpServletRequest request, byte[] body) {
            super(request);
            this.body = body;
        }

        @Override
        public ServletInputStream getInputStream() {
            ByteArrayInputStream source = new ByteArrayInputStream(body);
            return new ServletInputStream() {
                @Override public int read() { return source.read(); }
                @Override public int read(byte[] b, int off, int len) { return source.read(b, off, len); }
                @Override public boolean isFinished() { return source.available() == 0; }
                @Override public boolean isReady() { return true; }
                @Override public void setReadListener(ReadListener listener) { }
            };
        }
    }
}
