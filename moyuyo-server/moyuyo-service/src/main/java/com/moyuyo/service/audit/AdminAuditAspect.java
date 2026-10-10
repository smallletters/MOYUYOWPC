package com.moyuyo.service.audit;

import com.moyuyo.common.annotation.AdminAudit;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.ClientIpResolver;
import com.moyuyo.common.utils.LogMasker;
import com.moyuyo.dao.admin.entity.AuditLogEntity;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.Arrays;

/**
 * 管理后台写操作审计切面
 * <p>
 * 拦截所有标注 {@link AdminAudit} 的方法，自动写入 mo_audit_log：
 * <ul>
 *   <li>操作人：从 UserContextHolder 获取（JWT 已写入 userId / role）</li>
 *   <li>action / module：注解显式声明优先，module 缺省时取 URL 第一段</li>
 *   <li>resourceId / detail：支持 SpEL 引用方法参数</li>
 *   <li>IP / UA：从 HttpServletRequest 解析</li>
 *   <li>result：SUCCESS（无异常抛出）/ FAILURE（抛 Throwable）</li>
 * </ul>
 * 持久化路径：AuditLogEntity → AdminAuditPersister 异步队列 → mo_audit_log。
 * 业务线程仅入队（O(1)），写库开销转嫁给 flusher 线程，不阻塞主业务。
 * <p>
 * 关键设计：
 * <ul>
 *   <li>入队异常被 try/catch 隔离，绝不污染主业务返回</li>
 *   <li>记录失败时降级 log.info，便于排障（不抛错给用户）</li>
 *   <li>SpEL 解析失败时回退到原始字符串，不中断审计流程</li>
 *   <li>非 Web 场景（单元测试 / 调度任务）RequestContextHolder 为空，IP/UA 返回 "unknown"</li>
 * </ul>
 */
@Slf4j
@Aspect
@Component
public class AdminAuditAspect {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final AdminAuditPersister persister;
    private final AdminUserInfoResolver userInfoResolver;

    public AdminAuditAspect(AdminAuditPersister persister, AdminUserInfoResolver userInfoResolver) {
        this.persister = persister;
        this.userInfoResolver = userInfoResolver;
    }

    @Around("@annotation(adminAudit)")
    public Object around(ProceedingJoinPoint joinPoint, AdminAudit adminAudit) throws Throwable {
        // 提前解析 SpEL（在 try 之外，避免 finally 中再次访问已被覆盖的变量）
        String resourceId = resolveSpel(adminAudit.resourceId(), joinPoint);
        String detail = resolveSpel(adminAudit.detail(), joinPoint);

        // 操作人（来自 JwtAuthFilter 已写入的 ThreadLocal）
        Long operatorId = UserContextHolder.getUserId();
        // 解析可读操作人名称(name > email > username > uid 兜底),
        // 使用带 5 分钟缓存的解析器避免每条审计都查 DB
        String operatorName = userInfoResolver.resolveDisplayName(operatorId);

        // module 缺省时取 URL 第一段（/api/admin/{module}/...）
        String module = adminAudit.module();
        if (module == null || module.isEmpty()) {
            module = resolveModuleFromUri();
            if (module == null || module.isEmpty()) {
                module = "UNKNOWN";
            }
        }
        module = module.toUpperCase();

        String action = adminAudit.action() == null ? "UNKNOWN" : adminAudit.action().toUpperCase();
        String ip = resolveClientIp();
        String userAgent = resolveUserAgent();

        Object result = null;
        String resultStatus = "SUCCESS";
        String errorMessage = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            resultStatus = "FAILURE";
            errorMessage = t.getMessage();
            throw t;
        } finally {
            try {
                AuditLogEntity entity = new AuditLogEntity();
                entity.setOperatorId(operatorId);
                entity.setOperatorName(operatorName);
                entity.setAction(action);
                entity.setModule(module);
                entity.setResourceId(truncate(resourceId, 64));
                entity.setDetail(buildDetail(detail, adminAudit.logParams(), joinPoint, errorMessage));
                entity.setIp(ip);
                entity.setUserAgent(truncate(userAgent, 500));
                entity.setResult(resultStatus);
                boolean enqueued = persister.enqueue(entity);
                if (!enqueued) {
                    log.info("[admin-audit] 已丢弃: action={}, module={}, operator={}, detail={}, params={}",
                            action, module, operatorName, detail,
                            LogMasker.maskSensitiveKv(Arrays.toString(joinPoint.getArgs())));
                }
            } catch (Exception persistEx) {
                log.warn("[admin-audit] 持久化入队异常：action={}, module={}, detail={}", action, module, detail, persistEx);
            }
        }
    }

    /** 解析 SpEL 表达式 */
    private String resolveSpel(String expression, ProceedingJoinPoint joinPoint) {
        if (expression == null || expression.isEmpty()) {
            return "";
        }
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String[] paramNames = parameterNameDiscoverer.getParameterNames(signature.getMethod());
            Object[] args = joinPoint.getArgs();
            if (paramNames == null || args == null) {
                return expression;
            }
            EvaluationContext context = new StandardEvaluationContext();
            for (int i = 0; i < paramNames.length && i < args.length; i++) {
                context.setVariable(paramNames[i], args[i]);
            }
            Expression exp = parser.parseExpression(expression);
            Object value = exp.getValue(context);
            return value != null ? value.toString() : expression;
        } catch (Exception e) {
            log.debug("[admin-audit] SpEL 解析失败: {}", expression, e);
            return expression;
        }
    }

    /**
     * 组装最终 detail：优先用注解 detail，启用 logParams 时附加脱敏后的入参，
     * 失败时附加 errorMessage，便于运维定位。
     */
    private String buildDetail(String detail, boolean logParams, ProceedingJoinPoint joinPoint, String errorMessage) {
        StringBuilder sb = new StringBuilder();
        if (detail != null && !detail.isEmpty()) {
            sb.append(detail);
        }
        if (logParams) {
            sb.append(" | params=").append(LogMasker.maskSensitiveKv(Arrays.toString(joinPoint.getArgs())));
        }
        if (errorMessage != null && !errorMessage.isEmpty()) {
            sb.append(" | error=").append(truncate(errorMessage, 200));
        }
        String result = sb.toString();
        return truncate(result, 500);
    }

    /**
     * 解析模块名：/api/admin/{module}/... 的第一段路径
     */
    private String resolveModuleFromUri() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            String uri = attrs.getRequest().getRequestURI();
            if (uri == null) {
                return null;
            }
            String prefix = "/api/admin/";
            if (!uri.startsWith(prefix)) {
                return null;
            }
            String rest = uri.substring(prefix.length());
            int slash = rest.indexOf('/');
            return slash >= 0 ? rest.substring(0, slash) : rest;
        } catch (Exception e) {
            return null;
        }
    }

    private String resolveClientIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "unknown";
            }
            HttpServletRequest request = attrs.getRequest();
            return request != null ? ClientIpResolver.resolve(request) : "unknown";
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String resolveUserAgent() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return "unknown";
            }
            HttpServletRequest request = attrs.getRequest();
            String ua = request == null ? null : request.getHeader("User-Agent");
            return ua == null || ua.isEmpty() ? "unknown" : ua;
        } catch (Exception e) {
            return "unknown";
        }
    }

    private String truncate(String s, int max) {
        if (s == null) {
            return null;
        }
        return s.length() > max ? s.substring(0, max) : s;
    }
}
