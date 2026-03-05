package mohammadnuridin.todolist.core.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * LoggingAspect untuk menangkap method calls di Controller dan Service layer.
 *
 * Fitur:
 * - Log method entry dengan parameter
 * - Log method exit dengan return value
 * - Log execution time
 * - Log exception detail
 * - Mask sensitive data (password, token)
 */
@Aspect
@Component
@Slf4j
public class LoggingAspect extends AdvancedLoggingAspect {

    // ─── Pointcut untuk Controller ──────────────────────────────────────────────

    @Pointcut("execution(* mohammadnuridin.todolist.modules.auth.AuthController.*(..))")
    public void authControllerMethods() {
    }

    @Pointcut("execution(* mohammadnuridin.todolist.modules.user.UserController.*(..))")
    public void userControllerMethods() {
    }

    // ─── Pointcut untuk Service ─────────────────────────────────────────────────

    @Pointcut("execution(* mohammadnuridin.todolist.modules.auth.AuthService.*(..))")
    public void authServiceMethods() {
    }

    @Pointcut("execution(* mohammadnuridin.todolist.modules.user.UserService.*(..))")
    public void userServiceMethods() {
    }

    // ─── Combined Pointcut ─────────────────────────────────────────────────────

    @Pointcut("authControllerMethods() || userControllerMethods() || authServiceMethods() || userServiceMethods()")
    public void allTrackedMethods() {
    }

    // ─── Around Advice ─────────────────────────────────────────────────────────

    /**
     * Intercept semua method di controller dan service.
     * Log entry, exit, execution time, dan exception.
     */
    @Around("allTrackedMethods()")
    public Object logMethodExecution(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String fullMethodName = className + "." + methodName;

        // Extract dan mask parameter
        String args = maskSensitiveData(Arrays.stream(joinPoint.getArgs())
                .map(Object::toString)
                .collect(Collectors.joining(", ")));

        long startTime = System.currentTimeMillis();

        try {
            log.info("[ENTRY] {} - Args: {}", fullMethodName, args);
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            log.info("[EXIT] {} - Result: {} - Execution time: {}ms",
                    fullMethodName,
                    maskSensitiveData(result.toString()),
                    executionTime);

            return result;
        } catch (Throwable ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            logException(fullMethodName, ex, executionTime);
            throw ex;
        }
    }

    // ─── Before Advice untuk Detail Tracking ────────────────────────────────────

    @Before("authControllerMethods() || userControllerMethods()")
    public void logControllerMethodEntry(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        log.debug("[CONTROLLER_ENTRY] {}.{} called", className, methodName);
    }

    @Before("authServiceMethods() || userServiceMethods()")
    public void logServiceMethodEntry(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        log.debug("[SERVICE_ENTRY] {}.{} called", className, methodName);
    }

    // ─── AfterReturning Advice ──────────────────────────────────────────────────

    @AfterReturning(pointcut = "authControllerMethods() || userControllerMethods()", returning = "result")
    public void logControllerReturn(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        log.debug("[CONTROLLER_EXIT] {}.{} returned successfully", className, methodName);
    }

    @AfterReturning(pointcut = "authServiceMethods() || userServiceMethods()", returning = "result")
    public void logServiceReturn(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        log.debug("[SERVICE_EXIT] {}.{} returned successfully", className, methodName);
    }

    // ─── AfterThrowing Advice ──────────────────────────────────────────────────

    /**
     * Log exception yang terjadi di controller.
     */
    @AfterThrowing(pointcut = "authControllerMethods() || userControllerMethods()", throwing = "exception")
    public void logControllerException(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String fullMethodName = className + "." + methodName;

        if (exception instanceof ResponseStatusException rse) {
            log.warn("[CONTROLLER_ERROR] {} - Status: {} - Message: {}",
                    fullMethodName,
                    rse.getStatusCode(),
                    rse.getReason());
        } else {
            log.error("[CONTROLLER_ERROR] {} - Exception: {}",
                    fullMethodName,
                    exception.getClass().getSimpleName());
            log.error("Error details: ", exception);
        }
    }

    /**
     * Log exception yang terjadi di service.
     */
    @AfterThrowing(pointcut = "authServiceMethods() || userServiceMethods()", throwing = "exception")
    public void logServiceException(JoinPoint joinPoint, Throwable exception) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String fullMethodName = className + "." + methodName;

        if (exception instanceof ResponseStatusException rse) {
            log.warn("[SERVICE_ERROR] {} - Status: {} - Message: {}",
                    fullMethodName,
                    rse.getStatusCode(),
                    rse.getReason());
        } else {
            log.error("[SERVICE_ERROR] {} - Exception: {}",
                    fullMethodName,
                    exception.getClass().getSimpleName());
            log.error("Error details: ", exception);
        }
    }

    // ─── Utility Methods ────────────────────────────────────────────────────────

    /**
     * Mask sensitive data dalam log (password, token, dll).
     * Mencegah credential tercatat di log file.
     */
    private String maskSensitiveData(String data) {
        if (data == null) {
            return "null";
        }

        // Mask password
        data = data.replaceAll("(?i)\"password\"\\s*:\\s*\"[^\"]*\"",
                "\"password\":\"***MASKED***\"");
        data = data.replaceAll("(?i)password=[^,}\\s]*", "password=***MASKED***");

        // Mask refresh token
        data = data.replaceAll("(?i)\"refreshToken\"\\s*:\\s*\"[^\"]*\"",
                "\"refreshToken\":\"***MASKED***\"");
        data = data.replaceAll("(?i)refreshToken=[^,}\\s]*", "refreshToken=***MASKED***");

        // Mask access token
        data = data.replaceAll("(?i)\"accessToken\"\\s*:\\s*\"[^\"]*\"",
                "\"accessToken\":\"***MASKED***\"");
        data = data.replaceAll("(?i)accessToken=[^,}\\s]*", "accessToken=***MASKED***");

        // Mask token (general)
        data = data.replaceAll("(?i)\"token\"\\s*:\\s*\"[^\"]*\"",
                "\"token\":\"***MASKED***\"");
        data = data.replaceAll("(?i)token=[^,}\\s]*", "token=***MASKED***");

        return data;
    }

    /**
     * Log detail exception dengan execution time.
     */
    private void logException(String fullMethodName, Throwable ex, long executionTime) {
        if (ex instanceof ResponseStatusException rse) {
            HttpStatus status = (HttpStatus) rse.getStatusCode();
            String message = rse.getReason() != null ? rse.getReason() : "No details";

            log.warn("[EXCEPTION] {} - Status: {} - Message: {} - Execution time: {}ms",
                    fullMethodName,
                    status,
                    message,
                    executionTime);
        } else {
            log.error("[EXCEPTION] {} - Type: {} - Message: {} - Execution time: {}ms",
                    fullMethodName,
                    ex.getClass().getName(),
                    ex.getMessage(),
                    executionTime);
            log.error("Stack trace: ", ex);
        }
    }
}