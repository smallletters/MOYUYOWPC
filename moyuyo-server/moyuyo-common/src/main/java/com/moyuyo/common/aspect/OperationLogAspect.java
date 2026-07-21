package com.moyuyo.common.aspect;

import com.moyuyo.common.annotation.OperationLog;
import com.moyuyo.common.security.UserContextHolder;
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

import java.util.Arrays;

/**
 * 关键业务操作日志切面
 * <p>
 * 拦截所有标注 @OperationLog 的方法，自动记录：
 * - 操作人（从 UserContextHolder 获取）
 * - 操作类型
 * - 操作详情（支持 SpEL 表达式）
 * - 执行耗时
 */
@Slf4j
@Aspect
@Component
public class OperationLogAspect {

    private final SpelExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();

    @Around("@annotation(operationLog)")
    public Object around(ProceedingJoinPoint joinPoint, OperationLog operationLog) throws Throwable {
        long startTime = System.currentTimeMillis();

        // 解析操作详情中的 SpEL 表达式
        String detail = resolveSpel(operationLog.detail(), joinPoint);

        // 记录日志前缀
        String type = operationLog.type();
        Long userId = UserContextHolder.getUserId();
        String userInfo = userId != null ? "userId=" + userId : "anonymous";

        // 参数日志（敏感接口应关闭）
        String params = "";
        if (operationLog.logParams()) {
            params = ", params=" + Arrays.toString(joinPoint.getArgs());
        }

        Object result;
        try {
            result = joinPoint.proceed();
            long cost = System.currentTimeMillis() - startTime;
            log.info("操作日志: type={}, {}, detail={}, cost={}ms{}", type, userInfo, detail, cost, params);
            return result;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startTime;
            log.warn("操作日志(异常): type={}, {}, detail={}, cost={}ms, error={}{}",
                    type, userInfo, detail, cost, e.getMessage(), params);
            throw e;
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
}
