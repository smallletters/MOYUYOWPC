package com.moyuyo.service.audit;

import com.moyuyo.common.annotation.OperationLog;
import com.moyuyo.common.security.UserContextHolder;
import com.moyuyo.common.utils.ClientIpResolver;
import com.moyuyo.common.utils.LogMasker;
import com.moyuyo.dao.admin.entity.OperationLogEntity;
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
 * 关键业务操作日志切面
 * <p>
 * 拦截所有标注 @OperationLog 的方法，自动记录：
 * - 操作人（从 UserContextHolder 获取）
 * - 操作类型
 * - 操作详情（支持 SpEL 表达式）
 * - 执行耗时
 * - 客户端 IP（从 HttpServletRequest 解析）
 * <p>
 * 持久化路径：OperationLogEntity → OperationLogPersister 异步队列 → mo_operation_log。
 * 业务线程只负责入队（O(1)），写库开销转嫁给 flusher 线程，避免审计落库阻塞主业务。
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final OperationLogPersister persister;

    public OperationLogAspect(OperationLogPersister persister) {
        this.persister = persister;
    }

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 解析操作详情中的 SpEL 表达式
        String detail = resolveSpel(operationLog.detail(), joinPoint);

        String type = operationLog.type();
        Long userId = UserContextHolder.getUserId();

        Object result;
        boolean success = true;
        String errorMessage = null;
        try {
            result = joinPoint.proceed();
            return result;
        } catch (Throwable t) {
            success = false;
            errorMessage = t.getMessage();
            throw t;
        } finally {
            long cost = System.currentTimeMillis() - startTime;
            // 入队异步落库（非阻塞）；队列满时由 Persister 内部处理丢弃/阻塞策略
            try {
                OperationLogEntity entity = new OperationLogEntity();
                entity.setType(type);
                entity.setUserId(userId);
                entity.setUsername(UserContextHolder.getOperator());
                entity.setIp(resolveClientIp());
                entity.setDetail(detail);
                entity.setSuccess(success);
                entity.setErrorMessage(errorMessage != null && errorMessage.length() > 512
                        ? errorMessage.substring(0, 512) : errorMessage);
                entity.setCostMillis(cost);
                boolean enqueued = persister.enqueue(entity);
                if (!enqueued && operationLog.logParams()) {
                    // 丢弃场景下，INFO 日志兜底（便于联调 / 排障时仍能在业务日志看到请求参数）
                    // P1 修复：审计日志入参前必须经 LogMasker.maskSensitiveKv 脱敏，
                    // 避免 joinPoint.getArgs() 中含 password / token / secret 等敏感字段被原样写入业务日志
                    log.info("操作日志(已丢弃): type={}, userId={}, detail={}, cost={}ms, params={}",
                            type, userId, detail, cost,
                            LogMasker.maskSensitiveKv(Arrays.toString(joinPoint.getArgs())));
                }
            } catch (Exception persistEx) {
                // 持久化路径异常不影响主业务
                log.warn("[audit] OperationLog 持久化入队异常：type={}, detail={}", type, detail, persistEx);
            }
        }
    }

    /**
     * 解析 SpEL 表达式
     */
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
            log.debug("SpEL 表达式解析失败: {}", expression, e);
            return expression;
        }
    }

    /**
     * 解析客户端 IP（与 JwtAuthFilter / IpRateLimitFilter 共用 ClientIpResolver）
     * 单元测试或非 Web 场景下 RequestContextHolder 为空，返回 "unknown" 不抛 NPE
     */
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
}