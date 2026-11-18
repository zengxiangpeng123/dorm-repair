package com.zxp.dormrepair.common;

import com.zxp.dormrepair.auth.AuthFilter;
import com.zxp.dormrepair.domain.Actor;
import com.zxp.dormrepair.statemachine.ActorType;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;

/**
 * controller 取身份的唯一入口。身份由 {@link AuthFilter} 解析好放进请求属性，
 * 这里只读；读不到就是没登录（401）。
 *
 * <p>顺带提供角色校验：越权一律 403，且话术里写清楚"这个接口要什么身份"。
 */
public final class ActorResolver {

    private ActorResolver() {
    }

    public static Actor resolve(HttpServletRequest req) {
        Object attr = req.getAttribute(AuthFilter.ATTR);
        if (attr instanceof Actor actor) {
            return actor;
        }
        throw BizException.unauthorized("请先登录");
    }

    /** 令牌原文，登出用。 */
    public static String token(HttpServletRequest req) {
        Object t = req.getAttribute("dorm.token");
        return t instanceof String s ? s : null;
    }

    public static Actor require(Actor actor, ActorType... allowed) {
        for (ActorType t : allowed) {
            if (actor.type() == t) {
                return actor;
            }
        }
        String need = Arrays.stream(allowed).map(Enum::name).reduce((a, b) -> a + " / " + b).orElse("?");
        throw BizException.forbidden("这个接口只给 " + need + " 用，你现在的身份是 " + actor.type().name());
    }
}
