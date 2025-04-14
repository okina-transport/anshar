package no.rutebanken.anshar.aop;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StopWatch;

@Aspect
@Component
@Slf4j
public class LoggingAspect {

    @Value("${logging.method-execution-duration.threshold:5000}")
    private long threshold;

    @Pointcut("execution(* no.rutebanken.anshar.consistency..*(..)))")
    public void consistency() {
    }

    @Around("consistency()")
    public Object profileAllMethods(ProceedingJoinPoint proceedingJoinPoint) throws Throwable {
        MethodSignature methodSignature = (MethodSignature) proceedingJoinPoint.getSignature();

        String className = methodSignature.getDeclaringType().getSimpleName();
        String methodName = methodSignature.getName();

        final StopWatch stopWatch = new StopWatch();

        stopWatch.start();
        Object result = proceedingJoinPoint.proceed();
        stopWatch.stop();

        if (stopWatch.getTotalTimeMillis() > threshold) {
            log.info("Execution time of {}.{} : {} ms", className, methodName, stopWatch.getTotalTimeMillis());
        }

        return result;
    }
}
