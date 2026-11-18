package com.zxp.dormrepair.auth;

import com.zxp.dormrepair.domain.Actor;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 鉴权。除了少数公开路径，/api/v1/** 一律要带 {@code Authorization: Bearer <token>}。
 *
 * <p>通过之后把 {@link Actor} 放进请求属性 {@link #ATTR}，controller 用
 * {@code ActorResolver.resolve(req)} 取——controller 里不再自己解析身份。
 *
 * <p>{@code app.auth.allow-header=true} 时额外接受原来的三个 X-Actor-* 请求头，
 * 只给测试脚本使用；该开关在生产配置启动时会被拒绝。
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class AuthFilter extends OncePerRequestFilter {

    public static final String ATTR = "dorm.actor";

    private static final Set<String> PUBLIC = Set.of(
            "/api/v1/auth/login",
            "/api/v1/health");

    private final TokenStore tokens;
    private final boolean allowHeader;

    public AuthFilter(TokenStore tokens,
                      @Value("${app.auth.allow-header:false}") boolean allowHeader) {
        this.tokens = tokens;
        this.allowHeader = allowHeader;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest req) {
        String path = req.getRequestURI();
        if (!path.startsWith("/api/v1/")) {
            return true;
        }
        return PUBLIC.contains(path);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws ServletException, IOException {
        String header = req.getHeader("Authorization");
        String token = null;
        if (header != null && header.regionMatches(true, 0, "Bearer ", 0, 7)) {
            token = header.substring(7).trim();
        }
        TokenStore.Session session = tokens.resolve(token);
        if (session != null) {
            req.setAttribute(ATTR, session.actor());
            req.setAttribute("dorm.token", session.token());
            chain.doFilter(req, res);
            return;
        }
        if (allowHeader) {
            String type = req.getHeader("X-Actor-Type");
            String actorId = req.getHeader("X-Actor-Id");
            if (type != null && !type.isBlank() && actorId != null && !actorId.isBlank()) {
                try {
                    Actor actor = Actor.of(type, actorId, req.getHeader("X-Actor-Name"));
                    req.setAttribute(ATTR, actor);
                    chain.doFilter(req, res);
                    return;
                } catch (IllegalArgumentException ignored) {
                    // 测试开关也不接受无效身份，继续返回 401。
                }
            }
        }
        reject(res, token == null ? "请先登录" : "登录已失效，请重新登录");
    }

    private void reject(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.setContentType("application/json;charset=UTF-8");
        res.getWriter().write("{\"ok\":false,\"error\":{\"code\":\"UNAUTHORIZED\",\"message\":\"" + message + "\"}}");
    }
}
