package mohammadnuridin.todolist.modules.notification;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Test untuk pattern orchestration — parallel async calls
 * Contoh: DashboardService yang aggregate beberapa async service
 */
@SpringBootTest
class DashboardServiceTest {

    // Simulasi services yang dipanggil parallel
    // Di real project, inject DashboardService lalu mock dependencies-nya

    @Test
    void parallelAsyncCalls_shouldCompleteAllAndAggregate() throws Exception {
        // Simulasi 3 async calls parallel
        CompletableFuture<String> ordersFuture = CompletableFuture.supplyAsync(() -> {
            simulateDelay(100);
            return "orders-data";
        });

        CompletableFuture<String> profileFuture = CompletableFuture.supplyAsync(() -> {
            simulateDelay(100);
            return "profile-data";
        });

        CompletableFuture<String> statsFuture = CompletableFuture.supplyAsync(() -> {
            simulateDelay(100);
            return "stats-data";
        });

        long start = System.currentTimeMillis();

        // Tunggu semua selesai
        CompletableFuture.allOf(ordersFuture, profileFuture, statsFuture)
                .get(5, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - start;

        // Parallel = ~100ms, bukan 300ms (sequential)
        assertThat(elapsed).isLessThan(250);

        // Verifikasi semua hasil
        assertThat(ordersFuture.get()).isEqualTo("orders-data");
        assertThat(profileFuture.get()).isEqualTo("profile-data");
        assertThat(statsFuture.get()).isEqualTo("stats-data");
    }

    @Test
    void parallelAsyncCalls_whenOneFailsOthersShouldStillComplete() throws Exception {
        CompletableFuture<String> successFuture = CompletableFuture.supplyAsync(() -> {
            simulateDelay(50);
            return "success";
        });

        CompletableFuture<String> failedFuture = CompletableFuture.supplyAsync(() -> {
            simulateDelay(50);
            throw new RuntimeException("Service unavailable");
        });

        // allOf akan fail jika salah satu fail
        CompletableFuture<Void> all = CompletableFuture.allOf(successFuture, failedFuture);

        // Tunggu semua selesai (termasuk yang gagal)
        all.exceptionally(ex -> null).get(3, TimeUnit.SECONDS);

        // Yang sukses tetap selesai
        assertThat(successFuture.isDone()).isTrue();
        assertThat(successFuture.get()).isEqualTo("success");

        // Yang gagal completed exceptionally
        assertThat(failedFuture.isCompletedExceptionally()).isTrue();
    }

    @Test
    void asyncChaining_shouldExecuteInOrder() throws Exception {
        // Pattern: async call → transform result → async call lagi
        CompletableFuture<String> result = CompletableFuture
                .supplyAsync(() -> {
                    simulateDelay(50);
                    return "user-id-123";
                })
                .thenApplyAsync(userId -> {
                    simulateDelay(50);
                    return "profile-of-" + userId;
                })
                .thenApplyAsync(profile -> {
                    simulateDelay(50);
                    return profile.toUpperCase();
                });

        String finalResult = result.get(5, TimeUnit.SECONDS);
        assertThat(finalResult).isEqualTo("PROFILE-OF-USER-ID-123");
    }

    private void simulateDelay(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
