package mohammadnuridin.todolist.core.aspect;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

/**
 * AdvancedLoggingAspect (OPTIONAL)
 * 
 * Extenion dari LoggingAspect dengan fitur:
 * - Performance threshold warning
 * - Method-specific logging rules
 * - Request context tracking
 * - Audit trail untuk sensitive operations
 * 
 * Gunakan jika LoggingAspect sudah cukup, atau tambahkan fitur ini di
 * LoggingAspect utama.
 */
@Aspect
@Component
@Slf4j
public class AdvancedLoggingAspect {

    // Threshold (ms) untuk warning slow method
    private static final long SLOW_METHOD_THRESHOLD = 500;

    /**
     * Track slow method execution dengan warning level.
     * Jika execution time > SLOW_METHOD_THRESHOLD → log WARN
     */
    @Around("execution(* mohammadnuridin.todolist.modules.auth.AuthService.login(..)) || " +
            "execution(* mohammadnuridin.todolist.modules.auth.AuthService.refreshToken(..)) || " +
            "execution(* mohammadnuridin.todolist.modules.user.UserService.register(..))")
    public Object monitorPerformance(ProceedingJoinPoint joinPoint) throws Throwable {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();

        long startTime = System.currentTimeMillis();
        try {
            Object result = joinPoint.proceed();
            long executionTime = System.currentTimeMillis() - startTime;

            if (executionTime > SLOW_METHOD_THRESHOLD) {
                log.warn("[SLOW_METHOD] {}.{} took {}ms (threshold: {}ms)",
                        className, methodName, executionTime, SLOW_METHOD_THRESHOLD);
            } else {
                log.debug("[PERFORMANCE_OK] {}.{} took {}ms",
                        className, methodName, executionTime);
            }

            return result;
        } catch (Throwable ex) {
            long executionTime = System.currentTimeMillis() - startTime;
            log.error("[PERFORMANCE_ERROR] {}.{} failed after {}ms",
                    className, methodName, executionTime);
            throw ex;
        }
    }

    /**
     * Audit trail untuk sensitive operations:
     * - login, logout (authentication changes)
     * - register, update (user data changes)
     */
    @After("execution(* mohammadnuridin.todolist.modules.auth.AuthService.login(..)) || " +
            "execution(* mohammadnuridin.todolist.modules.auth.AuthService.logout(..)) || " +
            "execution(* mohammadnuridin.todolist.modules.user.UserService.register(..)) || " +
            "execution(* mohammadnuridin.todolist.modules.user.UserService.update(..))")
    public void auditSensitiveOperation(JoinPoint joinPoint) {
        String methodName = joinPoint.getSignature().getName();
        String className = joinPoint.getTarget().getClass().getSimpleName();
        String operation = className + "." + methodName;

        // Log audit trail dengan timestamp
        log.warn("[AUDIT] {} - Timestamp: {} - User: {}",
                operation,
                System.currentTimeMillis(),
                extractUserInfo(joinPoint));
    }

    /**
     * Log validation error dengan detail (khusus untuk bad request scenarios).
     */
    @AfterThrowing(pointcut = "execution(* mohammadnuridin.todolist.modules.*.*.*(..)) && " +
            "throwing(ex)", throwing = "ex")
    public void logValidationError(JoinPoint joinPoint, Exception ex) {
        if (ex instanceof ResponseStatusException rse) {
            if (rse.getStatusCode() == HttpStatus.BAD_REQUEST) {
                log.warn("[VALIDATION_ERROR] {} - Details: {}",
                        joinPoint.getSignature().getName(),
                        rse.getReason());
            }
        }
    }

    /**
     * Conditional logging berdasarkan method signature.
     * Contoh: log hanya method yang return WebResponse
     */
    @AfterReturning(pointcut = "execution(mohammadnuridin.todolist.common.dto.WebResponse+ mohammadnuridin.todolist.modules.auth.AuthController.*(..))", returning = "result")
    public void logControllerWebResponse(JoinPoint joinPoint, Object result) {
        String methodName = joinPoint.getSignature().getName();
        log.debug("[RESPONSE_INTERCEPTED] {} - Type: WebResponse", methodName);
    }

    /**
     * Track method invocation count (untuk analytics).
     * Bisa diintegrasikan dengan Micrometer untuk metrics.
     */
    @Pointcut("execution(* mohammadnuridin.todolist.modules.auth.AuthController.login(..))")
    public void loginEndpoint() {
    }

    @Before("loginEndpoint()")
    public void trackLoginAttempt(JoinPoint joinPoint) {
        // Bisa integrasikan dengan counter metrics
        log.info("[METRIC] Login attempt at {}", System.currentTimeMillis());
    }

    // ─── Helper Methods ────────────────────────────────────────────────────────

    /**
     * Extract user info dari joinPoint (misal dari @AuthenticationPrincipal).
     * Return: userId atau "ANONYMOUS" jika tidak ada.
     */
    private String extractUserInfo(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();

        // Cari parameter AuthenticatedUser atau String userId
        for (Object arg : args) {
            if (arg != null && arg.getClass().getSimpleName().equals("AuthenticatedUser")) {
                return arg.toString(); // Return toString() atau extract userId
            }
            if (arg instanceof String && ((String) arg).length() == 36) {
                // UUID pattern (36 chars)
                return (String) arg;
            }
        }

        return "SYSTEM";
    }

}