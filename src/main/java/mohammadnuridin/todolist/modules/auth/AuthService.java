package mohammadnuridin.todolist.modules.auth;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import mohammadnuridin.todolist.common.service.JwtService;
import mohammadnuridin.todolist.common.service.ValidationService;
import mohammadnuridin.todolist.modules.user.User;
import mohammadnuridin.todolist.modules.user.UserRepository;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final ValidationService validationService;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    // ─── Login ─────────────────────────────────────────────────────────────────

    /**
     * Strategy:
     * - Validate credentials.
     * - Generate short-lived ACCESS TOKEN (JWT) → returned to client (Authorization
     * header).
     * - Generate long-lived REFRESH TOKEN (opaque string) → stored in DB column
     * `token` + `token_expired_at`.
     */
    @Transactional
    public LoginResponse login(LoginRequest request) {
        validationService.validate(request);

        User user = userRepository.findByEmail(request.email())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "{user.not_found}"));

        if (!passwordEncoder.matches(request.password(), user.getPassword())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "{user.password.email.invalid}");
        }

        // Generate tokens
        String accessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String refreshToken = jwtService.generateRefreshToken();
        long refreshTokenExpiresAt = System.currentTimeMillis() + refreshTokenExpiration;

        // Persist refresh token in DB
        user.setToken(refreshToken);
        user.setTokenExpiredAt(refreshTokenExpiresAt);
        userRepository.saveAndFlush(user);

        log.info("User logged in: {}", user.getEmail());

        return new LoginResponse(
                accessToken,
                "Bearer",
                jwtService.getAccessTokenExpiration(),
                refreshToken);
    }

    // ─── Logout ────────────────────────────────────────────────────────────────

    /**
     * Invalidate refresh token by clearing DB columns.
     * The access token will naturally expire (stateless).
     */
    @Transactional
    public void logout(String userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "{user.not_found}"));

        user.setToken(null);
        user.setTokenExpiredAt(null);
        userRepository.saveAndFlush(user);

        log.info("User logged out: {}", user.getEmail());
    }

    // ─── Refresh Access Token ──────────────────────────────────────────────────

    /**
     * Client sends refresh token → server validates from DB → issues new access
     * token.
     */
    @Transactional
    public LoginResponse refreshToken(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh token is missing");
        }

        User user = userRepository.findFirstByToken(refreshToken)
                .orElseThrow(
                        () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "{user.refresh.token.invalid}"));

        if (user.getTokenExpiredAt() == null || System.currentTimeMillis() > user.getTokenExpiredAt()) {
            // Clear expired token
            user.setToken(null);
            user.setTokenExpiredAt(null);
            userRepository.saveAndFlush(user);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "{user.refresh.token.expired}");
        }

        // Rotate refresh token (best practice: setiap refresh, token baru digenerate)
        String newAccessToken = jwtService.generateAccessToken(user.getId(), user.getEmail());
        String newRefreshToken = jwtService.generateRefreshToken();
        long newExpiresAt = System.currentTimeMillis() + refreshTokenExpiration;

        user.setToken(newRefreshToken);
        user.setTokenExpiredAt(newExpiresAt);
        userRepository.save(user);

        log.info("Access token refreshed for: {}", user.getEmail());

        return new LoginResponse(
                newAccessToken,
                "Bearer",
                jwtService.getAccessTokenExpiration(),
                newRefreshToken);
    }
}