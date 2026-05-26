package com.tbm.careerpathlearning.aspect;

import com.tbm.careerpathlearning.exception.HazardException;
import org.aspectj.lang.JoinPoint;
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

    // Intercept all methods
    @Before("execution(* com.tbm.careerpathlearning..*(..))")
    public void logMethodStart(JoinPoint joinPoint) {
        logger.info("Entering: {}.{} with args={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                joinPoint.getArgs());
    }

    @AfterReturning(pointcut = "execution(* com.tbm.careerpathlearning..*(..))", returning = "result")
    public void logMethodReturn(JoinPoint joinPoint, Object result) {
        logger.info("Exiting: {}.{} with result={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                result);
    }

    @AfterThrowing(pointcut = "execution(* com.tbm.careerpathlearning..*(..))", throwing = "ex")
    public void logMethodException(JoinPoint joinPoint, Throwable ex) {
        if(ex instanceof HazardException) {
            //TODO: Save Hazard Error into DB
        }
        logger.error("Exception in: {}.{} message={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                ex.getMessage(), ex);
    }


}

