package cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.impl;

import cn.iocoder.yudao.framework.ratelimiter.core.annotation.RateLimiter;
import cn.iocoder.yudao.framework.ratelimiter.core.keyresolver.RateLimiterKeyResolver;
import cn.iocoder.yudao.framework.web.core.util.WebFrameworkUtils;
import org.aspectj.lang.JoinPoint;

/**
 * CO-TI: limite por pessoa e por operação. O {@link UserRateLimiterKeyResolver} inclui os argumentos na chave, então cada
 * texto diferente tem um limite próprio e o limite nunca é atingido.
 */
public class PessoaRateLimiterKeyResolver implements RateLimiterKeyResolver {

    @Override
    public String resolver(JoinPoint joinPoint, RateLimiter rateLimiter) {
        return joinPoint.getSignature().toLongString() + ":" + WebFrameworkUtils.getLoginUserId();
    }

}
