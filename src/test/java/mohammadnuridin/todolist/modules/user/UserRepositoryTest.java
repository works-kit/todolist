package mohammadnuridin.todolist.modules.user;

import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit Test — UserRepository
 *
 * Menggunakan @DataJpaTest (H2 in-memory) untuk tes yang murni database.
 *
 * Cakupan:
 * 1. Functional Testing : CRUD, custom query methods
 * 2. Data Testing : unique constraint, field nullability, UUID auto-gen
 * 3. Security Testing : token isolation antar user
 * 4. Concurrency Testing : race condition insert email duplikat
 */
@DataJpaTest
@ActiveProfiles("test")
@DisplayName("UserRepository Tests")
class UserRepositoryTest {

    @Autowired
    UserRepository userRepository;

    @AfterEach
    void cleanUp() {
        userRepository.deleteAll();
    }

    // ── Fixtures ──────────────────────────────────────────────────────────────

    private User buildUser(String name, String email) {
        return User.builder()
                .name(name)
                .email(email)
                .password("$2a$encoded-password")
                .build();
    }

    private User saveUser(String name, String email) {
        return userRepository.saveAndFlush(buildUser(name, email));
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 1. FUNCTIONAL TESTING — Dasar CRUD & Custom Queries
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Functional — CRUD & Custom Query")
    class FunctionalTests {

        @Test
        @DisplayName("Save user → UUID auto-generated, tidak null")
        void save_newUser_generatesUUID() {
            User user = buildUser("Alice", "alice@example.com");
            assertThat(user.getId()).isNull(); // belum disimpan

            User saved = userRepository.saveAndFlush(user);

            assertThat(saved.getId()).isNotNull().hasSize(36); // UUID format
        }

        @Test
        @DisplayName("findByEmail → menemukan user yang sudah disimpan")
        void findByEmail_existingEmail_returnsUser() {
            saveUser("Bob", "bob@example.com");

            Optional<User> result = userRepository.findByEmail("bob@example.com");

            assertThat(result).isPresent();
            assertThat(result.get().getName()).isEqualTo("Bob");
        }

        @Test
        @DisplayName("findByEmail → email tidak ada → Optional.empty()")
        void findByEmail_unknownEmail_returnsEmpty() {
            Optional<User> result = userRepository.findByEmail("ghost@example.com");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("findFirstByToken → menemukan user berdasarkan token")
        void findFirstByToken_existingToken_returnsUser() {
            User user = saveUser("Carol", "carol@example.com");
            user.setToken("my-refresh-token");
            userRepository.saveAndFlush(user);

            Optional<User> result = userRepository.findFirstByToken("my-refresh-token");

            assertThat(result).isPresent();
            assertThat(result.get().getEmail()).isEqualTo("carol@example.com");
        }

        @Test
        @DisplayName("findFirstByToken → token tidak ada → Optional.empty()")
        void findFirstByToken_unknownToken_returnsEmpty() {
            Optional<User> result = userRepository.findFirstByToken("nonexistent-token");

            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("existsByEmail → true jika email terdaftar")
        void existsByEmail_registered_returnsTrue() {
            saveUser("Dave", "dave@example.com");

            assertThat(userRepository.existsByEmail("dave@example.com")).isTrue();
        }

        @Test
        @DisplayName("existsByEmail → false jika email belum terdaftar")
        void existsByEmail_notRegistered_returnsFalse() {
            assertThat(userRepository.existsByEmail("nobody@example.com")).isFalse();
        }

        @Test
        @DisplayName("existsByEmailAndIdNot → false untuk email milik user sendiri")
        void existsByEmailAndIdNot_ownEmail_returnsFalse() {
            User user = saveUser("Eve", "eve@example.com");

            boolean result = userRepository.existsByEmailAndIdNot("eve@example.com", user.getId());

            assertThat(result).isFalse();
        }

        @Test
        @DisplayName("existsByEmailAndIdNot → true jika email dipakai user lain")
        void existsByEmailAndIdNot_takenByOther_returnsTrue() {
            saveUser("Frank", "frank@example.com");
            User other = saveUser("Other", "other@example.com");

            boolean result = userRepository.existsByEmailAndIdNot("frank@example.com", other.getId());

            assertThat(result).isTrue();
        }

        @Test
        @DisplayName("createdAt dan updatedAt ter-set otomatis saat pertama save")
        void save_newUser_timestampsAutoPopulated() {
            User user = saveUser("Grace", "grace@example.com");

            assertThat(user.getCreatedAt()).isNotNull();
            assertThat(user.getUpdatedAt()).isNotNull();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. DATA TESTING — Constraint & Integrity
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Data — Unique Constraint & Integrity")
    class DataIntegrityTests {

        @Test
        @DisplayName("Token bisa null (setelah logout)")
        void save_nullToken_isAllowed() {
            User user = saveUser("Ivy", "ivy@example.com");
            user.setToken(null);

            assertThatCode(() -> userRepository.saveAndFlush(user))
                    .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("Dua user boleh memiliki token = null tanpa konflik")
        void save_multipleUsersNullToken_noConflict() {
            User u1 = saveUser("Jack", "jack@example.com");
            User u2 = saveUser("Kate", "kate@example.com");
            u1.setToken(null);
            u2.setToken(null);

            assertThatCode(() -> {
                userRepository.saveAndFlush(u1);
                userRepository.saveAndFlush(u2);
            }).doesNotThrowAnyException();
        }

        @Test
        @DisplayName("tokenExpiredAt tersimpan dan terbaca dengan benar")
        void save_tokenExpiredAt_persistedCorrectly() {
            long expiry = System.currentTimeMillis() + 604_800_000L;
            User user = saveUser("Liam", "liam@example.com");
            user.setToken("test-token");
            user.setTokenExpiredAt(expiry);
            userRepository.saveAndFlush(user);

            User loaded = userRepository.findById(user.getId()).orElseThrow();

            assertThat(loaded.getTokenExpiredAt()).isEqualTo(expiry);
        }

        @Test
        @DisplayName("Update email → berhasil jika email baru belum dipakai user lain")
        void update_email_success_whenNotTaken() {
            User user = saveUser("Mia", "mia@example.com");
            user.setEmail("mia-new@example.com");

            assertThatCode(() -> userRepository.saveAndFlush(user))
                    .doesNotThrowAnyException();

            assertThat(userRepository.findByEmail("mia-new@example.com")).isPresent();
            assertThat(userRepository.findByEmail("mia@example.com")).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. SECURITY TESTING — Token Isolation
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Security — Token Isolation Antar User")
    class TokenSecurityTests {

        @Test
        @DisplayName("Token unik per user → findFirstByToken hanya mengembalikan pemiliknya")
        void findFirstByToken_returnsCorrectOwner() {
            User u1 = saveUser("Noah", "noah@example.com");
            User u2 = saveUser("Olivia", "olivia@example.com");

            u1.setToken("token-noah");
            u2.setToken("token-olivia");
            userRepository.saveAndFlush(u1);
            userRepository.saveAndFlush(u2);

            Optional<User> found = userRepository.findFirstByToken("token-noah");

            assertThat(found).isPresent();
            assertThat(found.get().getEmail()).isEqualTo("noah@example.com");
            // Pastikan bukan user lain
            assertThat(found.get().getId()).isNotEqualTo(u2.getId());
        }

        @Test
        @DisplayName("Setelah logout (token null), findFirstByToken tidak menemukan user")
        void findFirstByToken_afterLogout_returnsEmpty() {
            User user = saveUser("Peter", "peter@example.com");
            user.setToken("peter-token");
            userRepository.saveAndFlush(user);

            // Simulasi logout
            user.setToken(null);
            userRepository.saveAndFlush(user);

            Optional<User> result = userRepository.findFirstByToken("peter-token");

            assertThat(result).isEmpty();
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 4. CONCURRENCY TESTING — Race Condition Email Duplikat
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Concurrency — Race Condition Insert Duplikat")
    class ConcurrencyTests {

        /**
         * Simulasi race condition: N thread mencoba menyimpan user dengan email yang
         * sama.
         * DB unique constraint harus memastikan hanya SATU yang berhasil.
         *
         * Catatan: Di H2 in-memory test ini memverifikasi perilaku DB constraint,
         * bukan thread-safety Spring component (yang sudah dihandle
         * oleh @Transactional).
         */
        @Test
        @DisplayName("Concurrent insert email sama → hanya satu yang sukses, sisanya gagal")
        void concurrentInsert_sameEmail_onlyOneSucceeds() throws InterruptedException {
            int threadCount = 5;
            String sharedEmail = "concurrent@example.com";
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger failCount = new AtomicInteger(0);

            IntStream.range(0, threadCount).forEach(i -> new Thread(() -> {
                try {
                    startLatch.await();
                    User user = buildUser("User-" + i, sharedEmail);
                    userRepository.saveAndFlush(user);
                    successCount.incrementAndGet();
                } catch (DataIntegrityViolationException | jakarta.persistence.PersistenceException e) {
                    failCount.incrementAndGet();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } finally {
                    doneLatch.countDown();
                }
            }).start());

            startLatch.countDown();
            boolean finished = doneLatch.await(10, TimeUnit.SECONDS);

            assertThat(finished).isTrue();
            // Total harus = threadCount
            assertThat(successCount.get() + failCount.get()).isEqualTo(threadCount);
            // Hanya boleh ada 1 user dengan email ini di DB
            assertThat(userRepository.existsByEmail(sharedEmail)).isTrue();
            assertThat(userRepository.count()).isLessThanOrEqualTo(1L);
        }
    }
}
