package mohammadnuridin.todolist.modules.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.common.dto.WebResponse;
import mohammadnuridin.todolist.core.security.AuthenticatedUser;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

        private final AuthService authService;

        @Value("${app.jwt.refresh-token-expiration}")
        private long refreshTokenExpiration;

        // Cookie name constant
        private static final String REFRESH_TOKEN_COOKIE = "refresh_token";

        // ─── POST /apiauth/login ───────────────────────────────────────────────
        //
        // Dual-mode via header X-Client-Type:
        // X-Client-Type: web → refresh token via HttpOnly Cookie, body tidak ada
        // refreshToken
        // X-Client-Type: mobile → refresh token dikembalikan di body JSON
        // (default: mobile jika header tidak dikirim)

        @PostMapping("/login")
        @ResponseStatus(HttpStatus.OK)
        public WebResponse<LoginResponse> login(
                        @Valid @RequestBody LoginRequest request,
                        @RequestHeader(value = "X-Client-Type", defaultValue = "mobile") String clientType,
                        HttpServletResponse response) {
                LoginResponse result = authService.login(request);
                boolean isWeb = "web".equalsIgnoreCase(clientType);

                if (isWeb) {
                        // Web: set refresh token di HttpOnly Cookie
                        setRefreshTokenCookie(response, result.refreshToken());
                }

                LoginResponse body = new LoginResponse(
                                result.accessToken(),
                                "Bearer",
                                result.accessTokenExpiresIn(),
                                isWeb ? null : result.refreshToken() // Mobile dapat token di body
                );

                return WebResponse.<LoginResponse>builder()
                                .status("Login successful")
                                .code(HttpStatus.OK.value())
                                .data(body)
                                .build();
        }

        // ─── POST /apiauth/logout ──────────────────────────────────────────────

        @PostMapping("/logout")
        @ResponseStatus(HttpStatus.NO_CONTENT)
        public WebResponse<String> logout(
                        @AuthenticationPrincipal AuthenticatedUser currentUser,
                        @RequestHeader(value = "X-Client-Type", defaultValue = "mobile") String clientType,
                        HttpServletResponse response) {
                authService.logout(currentUser.getUserId());

                // Web: hapus cookie refresh token
                if ("web".equalsIgnoreCase(clientType)) {
                        clearRefreshTokenCookie(response);
                }

                return WebResponse.<String>builder()
                                .status("Logged out")
                                .code(HttpStatus.NO_CONTENT.value())
                                .data("Logged out successfully")
                                .build();
        }

        // ─── POST /apiauth/refresh ─────────────────────────────────────────────
        //
        // Dual-mode:
        // Web → baca refresh token dari Cookie (tidak perlu kirim body)
        // Mobile → baca refresh token dari body JSON { "refreshToken": "..." }

        @PostMapping("/refresh")
        @ResponseStatus(HttpStatus.OK)
        public WebResponse<LoginResponse> refresh(
                        @RequestHeader(value = "X-Client-Type", defaultValue = "mobile") String clientType,
                        @RequestBody(required = false) RefreshTokenRequest body,
                        HttpServletRequest request,
                        HttpServletResponse response) {
                boolean isWeb = "web".equalsIgnoreCase(clientType);
                String refreshToken;

                if (isWeb) {
                        // Baca dari HttpOnly Cookie
                        refreshToken = extractRefreshTokenFromCookie(request);
                } else {
                        // Baca dari body JSON
                        refreshToken = (body != null) ? body.refreshToken() : null;
                }

                LoginResponse result = authService.refreshToken(refreshToken);

                if (isWeb) {
                        // Rotate cookie — set cookie baru dengan token baru
                        setRefreshTokenCookie(response, result.refreshToken());
                }

                LoginResponse loginResponse = new LoginResponse(
                                result.accessToken(),
                                "Bearer",
                                result.accessTokenExpiresIn(),
                                isWeb ? null : result.refreshToken());

                return WebResponse.<LoginResponse>builder()
                                .status("Token refreshed")
                                .code(HttpStatus.OK.value())
                                .data(loginResponse)
                                .build();
        }

        // ─── Cookie Helpers ────────────────────────────────────────────────────────

        /**
         * Set HttpOnly Cookie untuk refresh token.
         *
         * Menggunakan addHeader() secara langsung — TIDAK menggunakan
         * response.addCookie() —
         * karena Java Cookie API tidak mendukung atribut SameSite secara native.
         * Memanggil addCookie() + addHeader() secara bersamaan akan menghasilkan
         * DUA Set-Cookie header: satu tanpa SameSite (dari addCookie) dan satu
         * dengan SameSite (dari addHeader), yang menyebabkan browser dan test
         * membaca header yang salah.
         *
         * Flag keamanan:
         * - HttpOnly : JS tidak bisa baca token (anti-XSS)
         * - SameSite=Strict: cookie tidak dikirim pada cross-site request (anti-CSRF)
         * - Path=/api/auth : cookie hanya dikirim ke endpoint auth, bukan ke seluruh
         * app
         * - Secure : aktifkan di production (HTTPS) dengan uncomment baris Secure
         */
        private void setRefreshTokenCookie(HttpServletResponse response, String token) {
                response.addHeader("Set-Cookie",
                                REFRESH_TOKEN_COOKIE + "=" + token
                                                + "; Max-Age=" + (refreshTokenExpiration / 1000)
                                                + "; Path=/api/auth"
                                                + "; HttpOnly"
                                                + "; SameSite=Strict"
                // + "; Secure" // aktifkan di production (wajib jika HTTPS)
                );
        }

        /**
         * Hapus cookie refresh token saat logout.
         */
        private void clearRefreshTokenCookie(HttpServletResponse response) {
                response.addHeader("Set-Cookie",
                                REFRESH_TOKEN_COOKIE + "="
                                                + "; Max-Age=0"
                                                + "; Path=/api/auth"
                                                + "; HttpOnly"
                                                + "; SameSite=Strict");
        }

        /**
         * Baca nilai refresh token dari request cookie.
         */
        private String extractRefreshTokenFromCookie(HttpServletRequest request) {
                if (request.getCookies() == null) {
                        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token cookie not found");
                }
                return Arrays.stream(request.getCookies())
                                .filter(c -> REFRESH_TOKEN_COOKIE.equals(c.getName()))
                                .map(Cookie::getValue)
                                .findFirst()
                                .orElseThrow(() -> new ResponseStatusException(
                                                HttpStatus.UNAUTHORIZED, "Refresh token cookie not found"));
        }
}