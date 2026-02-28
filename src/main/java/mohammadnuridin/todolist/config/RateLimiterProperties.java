package mohammadnuridin.todolist.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "app.rate-limiter")
public class RateLimiterProperties {

    /** Max requests diizinkan per window (global/default) */
    private int capacity = 60;

    /** Durasi window reset dalam detik */
    private long refillDurationSeconds = 60;

    /** Konfigurasi khusus endpoint auth (/auth/**) */
    private AuthConfig auth = new AuthConfig();

    @Getter
    @Setter
    public static class AuthConfig {
        private int capacity = 10;
        private long refillDurationSeconds = 60;
    }
}