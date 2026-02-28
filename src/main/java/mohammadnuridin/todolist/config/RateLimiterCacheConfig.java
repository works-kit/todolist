package mohammadnuridin.todolist.config;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@RequiredArgsConstructor
public class RateLimiterCacheConfig {

    private final RateLimiterProperties props;

    /**
     * Cache untuk bucket global (semua endpoint).
     * Entry expired otomatis setelah 10 menit idle — bersih sendiri.
     */
    @Bean("globalRateLimiterCache")
    public Cache<String, TokenBucket> globalRateLimiterCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(50_000) // maks 50k unique IP
                .build();
    }

    /**
     * Cache khusus auth endpoint (lebih ketat).
     */
    @Bean("authRateLimiterCache")
    public Cache<String, TokenBucket> authRateLimiterCache() {
        return Caffeine.newBuilder()
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .maximumSize(10_000)
                .build();
    }
}