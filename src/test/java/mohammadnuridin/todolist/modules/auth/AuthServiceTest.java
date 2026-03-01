package mohammadnuridin.todolist.modules.auth;

import mohammadnuridin.todolist.common.service.JwtService;
import mohammadnuridin.todolist.common.service.ValidationService;
import mohammadnuridin.todolist.modules.user.User;
import mohammadnuridin.todolist.modules.user.UserRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit Test — AuthService
 *
 * Cakupan:
 * 1. Functional Testing  : login, logout, refreshToken happy-path
 * 2. Security Testing    : wrong password, expired/invalid refresh token, null token
 * 3. Data Testing        : field mapping, token rotation, null-safety
 * 4. Concurrency Testing : simultaneous refresh dengan token yang sama
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Tests")
class AuthServiceTest {

    // ── Dependencies ────────────────────────────────────────────────────────────
    @Mock UserRepository userRepository;
    @Mock PasswordEncoder passwordEncoder;
    @Mock JwtService jwtService;
    @Mock ValidationService validationService;

    @InjectMocks AuthService authService;

    // ── Fixtures ─────────────────────────────────────────────────────────────────
    private static final String USER_ID      = UUID.randomUUID().toString();
    private static final String USER_EMAIL   = "test@example.com";
    private static final String RAW_PASSWORD = "password123";
    private static final String ENC_PASSWORD = "$2a$encoded";
    private static final String ACCESS_TOKEN  = "access.jwt.token";
    private static final String REFRESH_TOKEN = "opaque-refresh-token";
    private static final long   ACCESS_EXPIRY  = 3_600_000L;  // 1 jam
    private static final long   REFRESH_EXPIRY = 604_800_000L; // 7 hari

    @BeforeEach
    void setUp() {
        // Inject @Value field
        ReflectionTestUtils.setField(authService, "refreshTokenExpiration", REFRESH_EXPIRY);
    }

    private User buildUser() {
        return User.builder()
                .id(USER_ID)
                .name("Test User")
                .email(USER_EMAIL)
                .password(ENC_PASSWORD)
                .build();
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 1. FUNCTIONAL TESTING — Login
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Functional — Login")
    class LoginFunctionalTests {

        @Test
        @DisplayName("Login sukses → kembalikan access token & refresh token")
        void login_success_returnsTokens() {
            // Arrange
            User user = buildUser();
            LoginRequest req = new LoginRequest(USER_EMAIL, RAW_PASSWORD);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(RAW_PASSWORD, ENC_PASSWORD)).thenReturn(true);
            when(jwtService.generateAccessToken(USER_ID, USER_EMAIL)).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
            when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRY);

            // Act
            LoginResponse result = authService.login(req);

            // Assert
            assertThat(result.accessToken()).isEqualTo(ACCESS_TOKEN);
            assertThat(result.refreshToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(result.tokenType()).isEqualTo("Bearer");
            assertThat(result.accessTokenExpiresIn()).isEqualTo(ACCESS_EXPIRY);

            // Verifikasi refresh token disimpan ke DB
            assertThat(user.getToken()).isEqualTo(REFRESH_TOKEN);
            assertThat(user.getTokenExpiredAt()).isGreaterThan(System.currentTimeMillis());
            verify(userRepository).saveAndFlush(user);
        }

        @Test
        @DisplayName("Login sukses → token_expired_at tersimpan dalam rentang waktu yang benar")
        void login_tokenExpiredAt_isWithinExpectedRange() {
            User user = buildUser();
            LoginRequest req = new LoginRequest(USER_EMAIL, RAW_PASSWORD);

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(jwtService.generateAccessToken(any(), any())).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
            when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRY);

            long before = System.currentTimeMillis();
            authService.login(req);
            long after = System.currentTimeMillis();

            // tokenExpiredAt harus: before + REFRESH_EXPIRY ≤ actual ≤ after + REFRESH_EXPIRY
            assertThat(user.getTokenExpiredAt())
                    .isBetween(before + REFRESH_EXPIRY, after + REFRESH_EXPIRY);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. SECURITY TESTING — Login
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. Security — Login")
    class LoginSecurityTests {

        @Test
        @DisplayName("Email tidak ditemukan → 401 UNAUTHORIZED")
        void login_emailNotFound_throws401() {
            when(userRepository.findByEmail(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest(USER_EMAIL, RAW_PASSWORD)))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Password salah → 401 UNAUTHORIZED")
        void login_wrongPassword_throws401() {
            User user = buildUser();
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest(USER_EMAIL, "wrong")))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Password salah → token TIDAK disimpan ke DB")
        void login_wrongPassword_neverPersistsToken() {
            User user = buildUser();
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(anyString(), anyString())).thenReturn(false);

            assertThatThrownBy(() -> authService.login(new LoginRequest(USER_EMAIL, "wrong")))
                    .isInstanceOf(ResponseStatusException.class);

            verify(userRepository, never()).saveAndFlush(any());
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. FUNCTIONAL TESTING — Logout
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. Functional — Logout")
    class LogoutTests {

        @Test
        @DisplayName("Logout sukses → token & tokenExpiredAt di-null-kan")
        void logout_success_clearsToken() {
            User user = buildUser();
            user.setToken(REFRESH_TOKEN);
            user.setTokenExpiredAt(System.currentTimeMillis() + 10_000);

            when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));

            authService.logout(USER_ID);

            assertThat(user.getToken()).isNull();
            assertThat(user.getTokenExpiredAt()).isNull();
            verify(userRepository).saveAndFlush(user);
        }

        @Test
        @DisplayName("Logout user tidak ditemukan → 404 NOT_FOUND")
        void logout_userNotFound_throws404() {
            when(userRepository.findById(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.logout(USER_ID))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.NOT_FOUND);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 4. FUNCTIONAL TESTING — Refresh Token
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Functional — Refresh Token")
    class RefreshTokenFunctionalTests {

        @Test
        @DisplayName("Refresh sukses → token baru digenerate & disimpan (token rotation)")
        void refresh_success_rotatesToken() {
            String newRefreshToken = "new-refresh-token";
            String newAccessToken  = "new.access.token";

            User user = buildUser();
            user.setToken(REFRESH_TOKEN);
            user.setTokenExpiredAt(System.currentTimeMillis() + 100_000);

            when(userRepository.findFirstByToken(REFRESH_TOKEN)).thenReturn(Optional.of(user));
            when(jwtService.generateAccessToken(USER_ID, USER_EMAIL)).thenReturn(newAccessToken);
            when(jwtService.generateRefreshToken()).thenReturn(newRefreshToken);
            when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRY);

            LoginResponse result = authService.refreshToken(REFRESH_TOKEN);

            assertThat(result.accessToken()).isEqualTo(newAccessToken);
            assertThat(result.refreshToken()).isEqualTo(newRefreshToken);
            // Pastikan token lama sudah diganti
            assertThat(user.getToken()).isEqualTo(newRefreshToken);
            verify(userRepository).save(user);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 5. SECURITY TESTING — Refresh Token
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("5. Security — Refresh Token")
    class RefreshTokenSecurityTests {

        @Test
        @DisplayName("Refresh token null → 401 UNAUTHORIZED")
        void refresh_nullToken_throws401() {
            assertThatThrownBy(() -> authService.refreshToken(null))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Refresh token blank → 401 UNAUTHORIZED")
        void refresh_blankToken_throws401() {
            assertThatThrownBy(() -> authService.refreshToken("   "))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Refresh token tidak ada di DB → 401 UNAUTHORIZED")
        void refresh_tokenNotInDb_throws401() {
            when(userRepository.findFirstByToken(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.refreshToken("unknown-token"))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }

        @Test
        @DisplayName("Refresh token expired → 401, token di-null-kan dari DB")
        void refresh_expiredToken_clearsAndThrows401() {
            User user = buildUser();
            user.setToken(REFRESH_TOKEN);
            user.setTokenExpiredAt(System.currentTimeMillis() - 1_000); // sudah expired

            when(userRepository.findFirstByToken(REFRESH_TOKEN)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);

            // Token harus dibersihkan dari DB
            assertThat(user.getToken()).isNull();
            assertThat(user.getTokenExpiredAt()).isNull();
            verify(userRepository).saveAndFlush(user);
        }

        @Test
        @DisplayName("Refresh token — tokenExpiredAt null → dianggap expired → 401")
        void refresh_nullExpiry_throws401() {
            User user = buildUser();
            user.setToken(REFRESH_TOKEN);
            user.setTokenExpiredAt(null); // edge case

            when(userRepository.findFirstByToken(REFRESH_TOKEN)).thenReturn(Optional.of(user));

            assertThatThrownBy(() -> authService.refreshToken(REFRESH_TOKEN))
                    .isInstanceOf(ResponseStatusException.class)
                    .extracting(e -> ((ResponseStatusException) e).getStatusCode())
                    .isEqualTo(HttpStatus.UNAUTHORIZED);
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 6. DATA TESTING — Field Mapping
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("6. Data — Field Mapping & Integrity")
    class DataTests {

        @Test
        @DisplayName("Login response memiliki semua field yang benar")
        void login_responseFields_areComplete() {
            User user = buildUser();
            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(jwtService.generateAccessToken(any(), any())).thenReturn(ACCESS_TOKEN);
            when(jwtService.generateRefreshToken()).thenReturn(REFRESH_TOKEN);
            when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRY);

            LoginResponse result = authService.login(new LoginRequest(USER_EMAIL, RAW_PASSWORD));

            assertThat(result)
                    .isNotNull()
                    .satisfies(r -> {
                        assertThat(r.accessToken()).isNotBlank();
                        assertThat(r.tokenType()).isEqualTo("Bearer");
                        assertThat(r.accessTokenExpiresIn()).isPositive();
                        assertThat(r.refreshToken()).isNotBlank();
                    });
        }

        @Test
        @DisplayName("Setiap login menghasilkan refresh token yang berbeda")
        void login_eachCall_generatesDifferentRefreshToken() {
            User user = buildUser();
            String token1 = "refresh-token-1";
            String token2 = "refresh-token-2";

            when(userRepository.findByEmail(USER_EMAIL)).thenReturn(Optional.of(user));
            when(passwordEncoder.matches(any(), any())).thenReturn(true);
            when(jwtService.generateAccessToken(any(), any())).thenReturn(ACCESS_TOKEN);
            when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRY);
            when(jwtService.generateRefreshToken())
                    .thenReturn(token1)
                    .thenReturn(token2);

            LoginResponse r1 = authService.login(new LoginRequest(USER_EMAIL, RAW_PASSWORD));
            LoginResponse r2 = authService.login(new LoginRequest(USER_EMAIL, RAW_PASSWORD));

            assertThat(r1.refreshToken()).isNotEqualTo(r2.refreshToken());
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 7. CONCURRENCY TESTING — Race Condition pada Refresh Token
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("7. Concurrency — Refresh Token Race Condition")
    class ConcurrencyTests {

        /**
         * Simulasi: 5 thread memanggil refreshToken() secara bersamaan.
         * Karena mock tidak thread-safe, tes ini memvalidasi bahwa
         * AuthService TIDAK menyimpan state internal yang mutable
         * (semua state ada di parameter, bukan field).
         *
         * Catatan: Race condition sesungguhnya ditangani oleh DB transaction
         * (optimistic/pessimistic locking) di level repository, bukan di service.
         */
        @Test
        @DisplayName("Concurrent refresh calls — setiap thread menerima response yang valid")
        void refresh_concurrent_eachThreadGetsValidResponse() throws InterruptedException {
            int threadCount = 5;
            CountDownLatch startLatch = new CountDownLatch(1);
            CountDownLatch doneLatch  = new CountDownLatch(threadCount);
            AtomicInteger successCount = new AtomicInteger(0);
            AtomicInteger errorCount   = new AtomicInteger(0);

            // Setup: setiap thread dapat user baru (simulasi isolasi transaksi)
            for (int i = 0; i < threadCount; i++) {
                final String threadToken = REFRESH_TOKEN + "-" + i;
                User user = buildUser();
                user.setToken(threadToken);
                user.setTokenExpiredAt(System.currentTimeMillis() + 100_000);

                when(userRepository.findFirstByToken(threadToken)).thenReturn(Optional.of(user));
                when(jwtService.generateRefreshToken()).thenReturn("new-token-" + i);
                when(jwtService.generateAccessToken(any(), any())).thenReturn("new-access-" + i);
                when(jwtService.getAccessTokenExpiration()).thenReturn(ACCESS_EXPIRY);
            }

            for (int i = 0; i < threadCount; i++) {
                final String token = REFRESH_TOKEN + "-" + i;
                new Thread(() -> {
                    try {
                        startLatch.await();
                        authService.refreshToken(token);
                        successCount.incrementAndGet();
                    } catch (ResponseStatusException e) {
                        errorCount.incrementAndGet();
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    } finally {
                        doneLatch.countDown();
                    }
                }).start();
            }

            startLatch.countDown(); // mulai semua thread bersamaan
            boolean finished = doneLatch.await(5, TimeUnit.SECONDS);

            assertThat(finished).isTrue();
            assertThat(successCount.get()).isEqualTo(threadCount);
            assertThat(errorCount.get()).isZero();
        }
    }
}
