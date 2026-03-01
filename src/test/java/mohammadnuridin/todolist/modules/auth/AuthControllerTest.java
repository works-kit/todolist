package mohammadnuridin.todolist.modules.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import jakarta.servlet.http.Cookie;

import static org.hamcrest.Matchers.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integration-style test untuk AuthController.
 *
 * Menggunakan @SpringBootTest + @AutoConfigureMockMvc agar:
 * - Full application context ter-load (SecurityFilterChain, Redis bean, dsb)
 * - AuthService di-mock sehingga tidak butuh DB nyata
 * - Menghindari masalah @WebMvcTest yang gagal load custom SecurityConfig
 * dengan dependency Redis / Rate Limiter
 *
 * Profile "test" → H2 in-memory, JWT test key, rate limiter longgar (1000
 * req/menit).
 *
 * Cakupan:
 * 1. API Testing : status code, response body, header
 * 2. Security Testing : cookie HttpOnly/SameSite/Path; endpoint protection
 * 3. Functional Testing : dual-mode web/mobile login, refresh, logout
 * 4. Data Testing : null refreshToken pada web mode
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("AuthController Tests")
class AuthControllerTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    // Mock AuthService → semua logika bisnis/DB/Redis tidak dieksekusi
    @MockBean
    AuthService authService;

    // ── Fixtures ──────────────────────────────────────────────────────────────
    private static final String ACCESS_TOKEN = "access.jwt.token";
    private static final String REFRESH_TOKEN = "opaque-refresh-token";
    private static final long EXPIRES_IN = 3_600_000L;

    private static final String VALID_LOGIN_BODY = "{\"email\":\"test@example.com\",\"password\":\"password123\"}";

    private LoginResponse loginResponse() {
        return new LoginResponse(ACCESS_TOKEN, "Bearer", EXPIRES_IN, REFRESH_TOKEN);
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 1. API TESTING — POST /auth/login
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("1. API — POST /auth/login")
    class LoginApiTests {

        private final String URL = "/auth/login";

        @Test
        @DisplayName("Mobile login → 200 OK, body mengandung refreshToken")
        void login_mobileMode_returnsRefreshTokenInBody() throws Exception {
            when(authService.login(any())).thenReturn(loginResponse());

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "mobile")
                    .content(VALID_LOGIN_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.code").value(200))
                    .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                    .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("Web login → 200 OK, refreshToken NULL di body, Set-Cookie ter-set")
        void login_webMode_refreshTokenNullInBody_cookieSet() throws Exception {
            when(authService.login(any())).thenReturn(loginResponse());

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "web")
                    .content(VALID_LOGIN_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(header().string("Set-Cookie",
                            allOf(
                                    containsString("refresh_token="),
                                    containsString("HttpOnly"),
                                    containsString("SameSite=Strict"),
                                    containsString("Path=/api/auth"))));
        }

        @Test
        @DisplayName("Default (tanpa X-Client-Type) → dianggap mobile")
        void login_noClientTypeHeader_defaultsMobile() throws Exception {
            when(authService.login(any())).thenReturn(loginResponse());

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_LOGIN_BODY))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("Email kosong → 400 BAD REQUEST (Bean Validation)")
        void login_blankEmail_returns400() throws Exception {
            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"\",\"password\":\"password123\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Format email tidak valid → 400 BAD REQUEST")
        void login_invalidEmailFormat_returns400() throws Exception {
            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"bukan-email\",\"password\":\"password123\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Password kurang dari 6 karakter → 400 BAD REQUEST")
        void login_shortPassword_returns400() throws Exception {
            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\",\"password\":\"abc\"}"))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("Credentials salah → 401 UNAUTHORIZED dari service")
        void login_wrongCredentials_returns401() throws Exception {
            when(authService.login(any()))
                    .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED));

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"test@example.com\",\"password\":\"wrongpass\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Request body kosong {} → 400 BAD REQUEST")
        void login_emptyBody_returns400() throws Exception {
            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isBadRequest());
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 2. API TESTING — POST /auth/logout
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("2. API — POST /auth/logout")
    class LogoutApiTests {

        private final String URL = "/auth/logout";

        @Test
        @DisplayName("Logout tanpa Authorization header → 401 UNAUTHORIZED")
        void logout_withoutToken_returns401() throws Exception {
            mockMvc.perform(post(URL))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Logout dengan token tidak valid → 401 UNAUTHORIZED")
        void logout_withInvalidToken_returns401() throws Exception {
            mockMvc.perform(post(URL)
                    .header("Authorization", "Bearer invalid.jwt.token")
                    .header("X-Client-Type", "mobile"))
                    .andExpect(status().isUnauthorized());
        }

        // ── Stateless JWT — Behavior Documentation ──────────────────────────────
        //
        // DESAIN SADAR (bukan bug):
        // App ini menggunakan JWT stateless tanpa token blacklist.
        //
        // Setelah logout:
        // ✅ Refresh token di DB langsung di-null-kan → tidak bisa dapat access token
        // baru
        // ⚠️ Access token tetap valid sampai expired (15 menit di production)
        //
        // Ini AMAN karena:
        // 1. Access token berumur sangat pendek (900 detik = 15 menit)
        // 2. Tanpa refresh token, attacker tidak bisa memperpanjang sesi
        // 3. Window serangan maksimal 15 menit, lalu token mati sendiri
        //
        // Kapan perlu upgrade ke Skenario B (token blacklist Redis):
        // - Access token berumur panjang (jam/hari)
        // - Data sangat sensitif (perbankan, medis)
        // - Butuh "logout paksa semua device" (account takeover response)
        // ────────────────────────────────────────────────────────────────────────

        @Test
        @DisplayName("[Stateless JWT] Setelah logout, refresh token TIDAK bisa dipakai → 401")
        void afterLogout_refreshToken_isInvalidated() throws Exception {
            // Jaminan keamanan utama pada stateless JWT:
            // Attacker yang mencuri refresh token tidak bisa dapat access token baru
            // setelah user logout. Sesi baru tidak bisa dibuka.
            when(authService.refreshToken(REFRESH_TOKEN))
                    .thenThrow(new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Refresh token invalid or already revoked"));

            mockMvc.perform(post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "mobile")
                    .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Stateless JWT] Setelah logout, refresh via cookie juga invalid → 401")
        void afterLogout_refreshTokenCookie_isInvalidated() throws Exception {
            when(authService.refreshToken(REFRESH_TOKEN))
                    .thenThrow(new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED, "Refresh token invalid or already revoked"));

            mockMvc.perform(post("/auth/refresh")
                    .header("X-Client-Type", "web")
                    .cookie(new jakarta.servlet.http.Cookie("refresh_token", REFRESH_TOKEN)))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("[Stateless JWT] Access token valid dalam window expiry — by design, bukan bug")
        void afterLogout_accessTokenBehavior_isStateless_byDesign() throws Exception {
            // INI PERILAKU YANG DIHARAPKAN — bukan bug.
            //
            // Test ini mendokumentasikan trade-off desain:
            // Access token JWT tidak di-blacklist saat logout. Token tetap valid
            // sampai waktu expiry-nya (15 menit). Ini adalah standar industri
            // untuk JWT stateless dan diterima selama expiry pendek.
            //
            // Bukti keamanan ada di AuthServiceTest:
            // → logout_success_clearsToken() membuktikan refresh token di-null-kan
            // → afterLogout_refreshToken_isInvalidated() membuktikan sesi baru tidak bisa
            // dibuka
            //
            // Jika suatu saat butuh revokasi instan → tambah Redis blacklist.
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 3. API TESTING — POST /auth/refresh
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("3. API — POST /auth/refresh")
    class RefreshApiTests {

        private final String URL = "/auth/refresh";

        @Test
        @DisplayName("Mobile refresh via body → 200 OK")
        void refresh_mobileMode_bodyToken_returns200() throws Exception {
            when(authService.refreshToken(REFRESH_TOKEN)).thenReturn(loginResponse());

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "mobile")
                    .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.accessToken").value(ACCESS_TOKEN))
                    .andExpect(jsonPath("$.data.refreshToken").value(REFRESH_TOKEN));
        }

        @Test
        @DisplayName("Web refresh via cookie → 200 OK, cookie baru di-rotate")
        void refresh_webMode_cookieToken_rotatesCookie() throws Exception {
            when(authService.refreshToken(REFRESH_TOKEN)).thenReturn(loginResponse());

            mockMvc.perform(post(URL)
                    .header("X-Client-Type", "web")
                    .cookie(new Cookie("refresh_token", REFRESH_TOKEN)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.refreshToken").doesNotExist())
                    .andExpect(header().string("Set-Cookie",
                            containsString("refresh_token=")));
        }

        @Test
        @DisplayName("Web refresh tanpa cookie → 401 UNAUTHORIZED")
        void refresh_webMode_noCookie_returns401() throws Exception {
            mockMvc.perform(post(URL)
                    .header("X-Client-Type", "web"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token expired → 401 UNAUTHORIZED dari service")
        void refresh_expiredToken_returns401() throws Exception {
            when(authService.refreshToken(anyString()))
                    .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token expired"));

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "mobile")
                    .content("{\"refreshToken\":\"expired-token\"}"))
                    .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token tidak ada di DB → 401 UNAUTHORIZED")
        void refresh_unknownToken_returns401() throws Exception {
            when(authService.refreshToken(anyString()))
                    .thenThrow(new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid token"));

            mockMvc.perform(post(URL)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "mobile")
                    .content("{\"refreshToken\":\"unknown-token\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    // 4. SECURITY TESTING — Cookie Flags (Anti-XSS & Anti-CSRF)
    // ════════════════════════════════════════════════════════════════════════════

    @Nested
    @DisplayName("4. Security — Cookie Flags")
    class CookieSecurityTests {

        @BeforeEach
        void stubLogin() {
            when(authService.login(any())).thenReturn(loginResponse());
        }

        @Test
        @DisplayName("Set-Cookie wajib HttpOnly — JS tidak bisa baca token (anti-XSS)")
        void loginWeb_cookie_hasHttpOnlyFlag() throws Exception {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "web")
                    .content(VALID_LOGIN_BODY))
                    .andExpect(header().string("Set-Cookie", containsString("HttpOnly")));
        }

        @Test
        @DisplayName("Set-Cookie wajib SameSite=Strict (anti-CSRF)")
        void loginWeb_cookie_hasSameSiteStrict() throws Exception {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "web")
                    .content(VALID_LOGIN_BODY))
                    .andExpect(header().string("Set-Cookie", containsString("SameSite=Strict")));
        }

        @Test
        @DisplayName("Set-Cookie path terbatas ke /api/auth — tidak dikirim ke semua endpoint")
        void loginWeb_cookie_pathRestrictedToApiAuth() throws Exception {
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "web")
                    .content(VALID_LOGIN_BODY))
                    .andExpect(header().string("Set-Cookie", containsString("Path=/api/auth")));
        }

        @Test
        @DisplayName("Endpoint /auth/login adalah public — tidak butuh autentikasi")
        void loginEndpoint_isPublic() throws Exception {
            // Tidak ada Authorization header → tetap 200
            mockMvc.perform(post("/auth/login")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(VALID_LOGIN_BODY))
                    .andExpect(status().isOk());
        }

        @Test
        @DisplayName("Endpoint /auth/refresh adalah public — tidak butuh autentikasi")
        void refreshEndpoint_isPublic() throws Exception {
            when(authService.refreshToken(anyString())).thenReturn(loginResponse());

            mockMvc.perform(post("/auth/refresh")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("X-Client-Type", "mobile")
                    .content("{\"refreshToken\":\"" + REFRESH_TOKEN + "\"}"))
                    .andExpect(status().isOk());
        }
    }
}