package mohammadnuridin.todolist.config;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Simple Token Bucket — thread-safe, tidak butuh Redis.
 * Setiap IP punya bucket sendiri yang disimpan di Caffeine cache.
 */
public class TokenBucket {

    private final int capacity;
    private final long refillDurationMillis;

    private final AtomicInteger tokens;
    private final AtomicLong lastRefillTimestamp;

    public TokenBucket(int capacity, long refillDurationSeconds) {
        this.capacity = capacity;
        this.refillDurationMillis = refillDurationSeconds * 1000L;
        this.tokens = new AtomicInteger(capacity);
        this.lastRefillTimestamp = new AtomicLong(Instant.now().toEpochMilli());
    }

    /**
     * Coba konsumsi 1 token.
     *
     * @return true jika request diizinkan, false jika rate limit tercapai
     */
    public synchronized boolean tryConsume() {
        refillIfNeeded();
        if (tokens.get() > 0) {
            tokens.decrementAndGet();
            return true;
        }
        return false;
    }

    /** Kembalikan sisa token (untuk header X-RateLimit-Remaining) */
    public int getRemainingTokens() {
        refillIfNeeded();
        return tokens.get();
    }

    /** Waktu (epoch ms) kapan bucket akan di-refill berikutnya */
    public long getNextRefillTime() {
        return lastRefillTimestamp.get() + refillDurationMillis;
    }

    private void refillIfNeeded() {
        long now = Instant.now().toEpochMilli();
        long last = lastRefillTimestamp.get();
        if (now - last >= refillDurationMillis) {
            tokens.set(capacity);
            lastRefillTimestamp.set(now);
        }
    }
}