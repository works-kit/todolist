package mohammadnuridin.todolist.modules.notification;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

@Service
public class NotificationService {

    /**
     * Fire & forget — caller tidak menunggu selesai
     */
    @Async
    public void sendEmail(String to) {
        try {
            Thread.sleep(100); // simulasi latency
            System.out.println("Email sent to: " + to + " | thread: " + Thread.currentThread());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("sendEmail interrupted", e);
        }
    }

    /**
     * Async dengan return value
     */
    @Async
    public CompletableFuture<String> sendEmailWithResult(String to) {
        try {
            Thread.sleep(100);
            String result = "Email sent to: " + to;
            System.out.println(result + " | thread: " + Thread.currentThread());
            return CompletableFuture.completedFuture(result);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return CompletableFuture.failedFuture(e);
        }
    }

    /**
     * Simulasi async yang gagal — untuk testing exception handler
     */
    @Async
    public void sendEmailWithException(String to) {
        throw new RuntimeException("Simulated email failure for: " + to);
    }
}
