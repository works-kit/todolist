package mohammadnuridin.todolist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;

@Slf4j
@Component
public class RateLimiterFilter extends OncePerRequestFilter {

    private final Cache<String, TokenBucket> globalCache;
    private final Cache<String, TokenBucket> authCache;
    private final RateLimiterProperties props;
    private final ObjectMapper objectMapper;

    // Constructor manual agar @Qualifier bisa dipakai
    public RateLimiterFilter(
            @Qualifier("globalRateLimiterCache") Cache<String, TokenBucket> globalCache,
            @Qualifier("authRateLimiterCache") Cache<String, TokenBucket> authCache,
            RateLimiterProperties props,
            ObjectMapper objectMapper) {
        this.globalCache = globalCache;
        this.authCache = authCache;
        this.props = props;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String clientIp = resolveClientIp(request);
        String path = request.getRequestURI();

        boolean isAuthEndpoint = path.contains("/auth/");
        TokenBucket bucket = resolveBucket(clientIp, isAuthEndpoint);

        int capacity = isAuthEndpoint ? props.getAuth().getCapacity() : props.getCapacity();
        long refillSeconds = isAuthEndpoint
                ? props.getAuth().getRefillDurationSeconds()
                : props.getRefillDurationSeconds();

        // Set informatif rate-limit headers
        response.setIntHeader("X-RateLimit-Limit", capacity);
        response.setIntHeader("X-RateLimit-Remaining", Math.max(0, bucket.getRemainingTokens() - 1));
        response.setDateHeader("X-RateLimit-Reset", bucket.getNextRefillTime());

        if (!bucket.tryConsume()) {
            log.warn("Rate limit exceeded for IP: {} on path: {}", clientIp, path);
            sendTooManyRequestsResponse(response, refillSeconds);
            return;
        }

        filterChain.doFilter(request, response);
    }

    private TokenBucket resolveBucket(String clientIp, boolean isAuth) {
        if (isAuth) {
            return authCache.get(clientIp, ip -> new TokenBucket(props.getAuth().getCapacity(),
                    props.getAuth().getRefillDurationSeconds()));
        }
        return globalCache.get(clientIp, ip -> new TokenBucket(props.getCapacity(), props.getRefillDurationSeconds()));
    }

    private String resolveClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        String realIp = request.getHeader("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequestsResponse(HttpServletResponse response,
            long retryAfterSeconds) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));

        Map<String, Object> body = Map.of(
                "status", 429,
                "error", "Too Many Requests",
                "message", "Rate limit exceeded. Please try again later.",
                "timestamp", Instant.now().toString());

        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}