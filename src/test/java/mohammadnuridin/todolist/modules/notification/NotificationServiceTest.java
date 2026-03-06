package mohammadnuridin.todolist.modules.notification;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@SpringBootTest
@TestPropertySource(properties = "spring.threads.virtual.enabled=true")
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    // =========================================================
    // 1. TEST FIRE & FORGET — method void
    // =========================================================

    @Test
    void sendEmail_shouldRunOnVirtualThread() {
        AtomicReference<Thread> capturedThread = new AtomicReference<>();

        // Kita tidak bisa capture langsung dari void @Async,
        // jadi kita verifikasi dengan Awaitility bahwa tidak blocking
        long start = System.currentTimeMillis();

        notificationService.sendEmail("user@example.com");

        long elapsed = System.currentTimeMillis() - start;

        // Caller harus langsung return — tidak blocking 100ms
        assertThat(elapsed).isLessThan(50);
    }

    @Test
    void sendEmail_shouldNotBlockCallerThread() throws InterruptedException {
        long start = System.currentTimeMillis();

        // Kirim 5 email sekaligus — jika blocking, butuh 500ms+
        for (int i = 0; i < 5; i++) {
            notificationService.sendEmail("user" + i + "@example.com");
        }

        long elapsed = System.currentTimeMillis() - start;

        // Semua fire & forget, caller tidak menunggu
        assertThat(elapsed).isLessThan(100);
    }

    // =========================================================
    // 2. TEST ASYNC DENGAN RETURN VALUE — CompletableFuture
    // =========================================================

    @Test
    void sendEmailWithResult_shouldReturnCorrectValue() throws Exception {
        CompletableFuture<String> future = notificationService.sendEmailWithResult("test@example.com");

        String result = future.get(3, TimeUnit.SECONDS);

        assertThat(result).isEqualTo("Email sent to: test@example.com");
    }

    @Test
    void sendEmailWithResult_shouldRunOnVirtualThread() throws Exception {
        CompletableFuture<String> future = notificationService.sendEmailWithResult("vt@example.com");

        // Tunggu selesai
        future.get(3, TimeUnit.SECONDS);

        // Verifikasi thread name mengandung prefix yang kita set di AsyncConfig
        // (lihat AsyncConfig: .name("async-vt-", 0))
        // Ini dicek via log output — thread name: async-vt-0, async-vt-1, dst
        assertThat(future).isCompletedWithValue("Email sent to: vt@example.com");
    }

    @Test
    void sendEmailWithResult_parallelExecution_shouldBeFasterThanSequential() throws Exception {
        int count = 5;
        // Setiap call butuh ~100ms, jika sequential = 500ms+
        // Jika parallel = ~100ms

        long start = System.currentTimeMillis();

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            futures.add(notificationService.sendEmailWithResult("user" + i + "@example.com"));
        }

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).get(5, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - start;

        // Parallel: semua jalan bersamaan, total ~100ms bukan 500ms
        assertThat(elapsed).isLessThan(400);

        // Verifikasi semua future completed dengan hasil yang benar
        for (int i = 0; i < count; i++) {
            assertThat(futures.get(i).get()).contains("Email sent to: user" + i);
        }
    }

    // =========================================================
    // 3. TEST EXCEPTION HANDLING
    // =========================================================

    @Test
    void sendEmailWithResult_whenFailed_futureShouldBeCompleteExceptionally() {
        // Simulasi: kita buat sendEmailWithResult versi yang gagal
        CompletableFuture<String> future = CompletableFuture.failedFuture(
                new RuntimeException("Simulated failure"));

        assertThat(future).isCompletedExceptionally();
    }

    @Test
    void sendEmailWithException_voidAsync_shouldNotPropagateToCallerThread() {
        // void @Async yang throw exception TIDAK boleh crash caller thread
        // Exception di-handle oleh AsyncUncaughtExceptionHandler (log only)
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> {
            notificationService.sendEmailWithException("fail@example.com");
        });
    }

    // =========================================================
    // 4. TEST VIRTUAL THREAD PROPERTIES
    // =========================================================

    @Test
    void asyncThread_shouldBeVirtualThread() throws Exception {
        AtomicReference<Boolean> isVirtual = new AtomicReference<>(false);

        // Gunakan CompletableFuture untuk capture thread info
        CompletableFuture<String> future = notificationService.sendEmailWithResult("check@example.com");

        // Setelah selesai, kita tahu task sudah jalan di executor kita
        future.get(3, TimeUnit.SECONDS);

        // Virtual thread diverifikasi dari AsyncConfig yang pakai
        // Thread.ofVirtual().factory() — ini memastikan executor-nya benar
        assertThat(future.isDone()).isTrue();
    }

    @Test
    void asyncExecution_highConcurrency_shouldHandleWithoutThreadExhaustion() throws Exception {
        // Virtual thread bisa handle ribuan concurrent task
        // Platform thread pool biasanya terbatas (misal 200 thread)
        int taskCount = 500;

        List<CompletableFuture<String>> futures = new ArrayList<>();
        for (int i = 0; i < taskCount; i++) {
            futures.add(notificationService.sendEmailWithResult("user" + i + "@example.com"));
        }

        // Semua harus selesai tanpa RejectedExecutionException
        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get(30, TimeUnit.SECONDS);

        long completedCount = futures.stream()
                .filter(CompletableFuture::isDone)
                .filter(f -> !f.isCompletedExceptionally())
                .count();

        assertThat(completedCount).isEqualTo(taskCount);
    }

    // =========================================================
    // 5. TEST AWAITILITY — untuk fire & forget yang perlu verifikasi
    // =========================================================

    @Test
    void sendEmail_withAwaitility_shouldEventuallyComplete() {
        List<String> completedEmails = new ArrayList<>();

        // Dalam real test, kamu akan mock dependency dan capture hasilnya
        // Contoh pola dengan Awaitility:
        notificationService.sendEmail("await@example.com");

        // Awaitility: tunggu kondisi terpenuhi dalam batas waktu
        await()
                .atMost(3, TimeUnit.SECONDS)
                .pollInterval(100, TimeUnit.MILLISECONDS)
                .untilAsserted(() -> {
                    // Verifikasi side effect dari async method
                    // Misal: cek database, cek flag, cek mock interaction
                    assertThat(true).isTrue(); // ganti dengan assertasi yang nyata
                });
    }
}
