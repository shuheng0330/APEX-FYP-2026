package com.tbm.careerpathlearning.aspect;

import com.tbm.careerpathlearning.exception.HazardException;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.AfterThrowing;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Aspect
@Component
public class LoggingAspect {

    private static final Logger logger = LoggerFactory.getLogger(LoggingAspect.class);

    /**
     * All methods, except the two classes that hold a whole SOP's text or an AI prompt: this aspect prints arguments
     * and results, and the personal data in those must stay out of the logs (FR-06-17).
     */
    @Pointcut("execution(* com.tbm.careerpathlearning..*(..))"
            + " && !within(com.tbm.careerpathlearning.service.DocumentParserService)"
            + " && !within(com.tbm.careerpathlearning.service.GeminiClient)")
    private void logged() {
    }

    // Intercept all methods
    @Before("logged()")
    public void logMethodStart(JoinPoint joinPoint) {
        logger.info("Entering: {}.{} with args={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                joinPoint.getArgs());
    }

    @AfterReturning(pointcut = "logged()", returning = "result")
    public void logMethodReturn(JoinPoint joinPoint, Object result) {
        logger.info("Exiting: {}.{} with result={}",
                joinPoint.getSignature().getDeclaringTypeName(),
                joinPoint.getSignature().getName(),
                result);
    }

    @AfterThrowing(pointcut = "logged()", throwing = "ex")
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

