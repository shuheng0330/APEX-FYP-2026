package com.tbm.careerpathlearning.aspect;

import com.tbm.careerpathlearning.exception.HazardException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);
    private static final String APPLICATION_METHOD = "execution(* com.tbm.careerpathlearning..*(..))"
            + " && !within(com.tbm.careerpathlearning.aspect.MetadataOnlyLogScope)";

    // Intercept all methods
    @Before(APPLICATION_METHOD)
    public void logMethodStart(JoinPoint joinPoint) {
        if (metadataOnly(joinPoint)) {
            logger.info("Entering: {}.{} [payload omitted]", joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName());
            return;
        }
        logger.info("Entering: {}.{} with args={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                joinPoint.getArgs());
    }

    @AfterReturning(pointcut = APPLICATION_METHOD, returning = "result")
    public void logMethodReturn(JoinPoint joinPoint, Object result) {
        if (metadataOnly(joinPoint)) {
            logger.info("Exiting: {}.{} [payload omitted]", joinPoint.getSignature().getDeclaringTypeName(),
                    joinPoint.getSignature().getName());
            return;
        }
        logger.info("Exiting: {}.{} with result={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                result);
    }

    @AfterThrowing(pointcut = APPLICATION_METHOD, throwing = "ex")
    public void logMethodException(JoinPoint joinPoint, Throwable ex) {
        if (metadataOnly(joinPoint)) {
            logger.error("Exception in: {}.{} type={} [payload omitted]",
                    joinPoint.getSignature().getDeclaringTypeName(), joinPoint.getSignature().getName(),
                    ex.getClass().getSimpleName());
            return;
        }
        if(ex instanceof HazardException) {
            //TODO: Save Hazard Error into DB
        }
        logger.error("Exception in: {}.{} message={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                ex.getMessage(), ex);
    }


    private boolean metadataOnly(JoinPoint point) {
        if (MetadataOnlyLogScope.active()) return true;
        var signature = point.getSignature();
        return AnnotatedElementUtils.hasAnnotation(signature.getDeclaringType(), MetadataOnlyLogging.class)
                || (point.getThis() != null && java.util.Arrays.stream(point.getThis().getClass().getInterfaces())
                        .anyMatch(type -> AnnotatedElementUtils.hasAnnotation(type, MetadataOnlyLogging.class)))
                || (point.getTarget() != null && AnnotatedElementUtils.hasAnnotation(
                        point.getTarget().getClass(), MetadataOnlyLogging.class))
                || (signature instanceof MethodSignature method && AnnotatedElementUtils.hasAnnotation(
                        method.getMethod(), MetadataOnlyLogging.class));
    }
}

