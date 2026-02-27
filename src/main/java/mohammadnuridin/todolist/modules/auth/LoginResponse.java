package mohammadnuridin.todolist.modules.auth;

/**
 * Response login.
 *
 * - accessToken : selalu dikembalikan di body (dipakai Web & Mobile)
 * - refreshToken : dikembalikan di body HANYA untuk Mobile.
 * Untuk Web, refresh token dikirim via HttpOnly Cookie
 * sehingga field ini null.
 */
public record LoginResponse(
                String accessToken,
                String tokenType,
                long accessTokenExpiresIn,
                String refreshToken // null jika client Web (pakai HttpOnly Cookie)
) {
}