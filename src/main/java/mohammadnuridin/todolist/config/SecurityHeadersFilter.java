package mohammadnuridin.todolist.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Filter untuk menambahkan security headers pada setiap HTTP response.
 *
 * Headers yang di-inject:
 * - X-Content-Type-Options : cegah MIME sniffing
 * - X-Frame-Options : cegah clickjacking
 * - X-XSS-Protection : XSS filter browser lama
 * - Strict-Transport-Security : paksa HTTPS (HSTS)
 * - Content-Security-Policy : batasi sumber konten
 * - Referrer-Policy : kontrol info referrer
 * - Permissions-Policy : batasi fitur browser
 * - Cache-Control : cegah cache respons sensitif
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

        @Override
        protected void doFilterInternal(HttpServletRequest request,
                        HttpServletResponse response,
                        FilterChain filterChain) throws ServletException, IOException {

                // ── Cegah MIME type sniffing ──
                response.setHeader("X-Content-Type-Options", "nosniff");

                // ── Cegah Clickjacking ──
                response.setHeader("X-Frame-Options", "DENY");

                // ── XSS Protection (untuk browser lama) ──
                response.setHeader("X-XSS-Protection", "1; mode=block");

                // ── HSTS: paksa HTTPS selama 1 tahun, termasuk subdomain ──
                // Aktifkan hanya di production (HTTPS sudah dikonfigurasi)
                response.setHeader("Strict-Transport-Security",
                                "max-age=31536000; includeSubDomains; preload");

                // ── Content Security Policy ──
                // Sesuaikan jika aplikasi serve frontend/Swagger UI
                response.setHeader("Content-Security-Policy",
                                "default-src 'self'; " +
                                                "script-src 'self'; " +
                                                "style-src 'self' 'unsafe-inline'; " +
                                                "img-src 'self' data:; " +
                                                "font-src 'self'; " +
                                                "connect-src 'self'; " +
                                                "frame-ancestors 'none'; " +
                                                "form-action 'self'");

                // ── Referrer Policy ──
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // ── Permissions Policy: matikan fitur browser yang tidak dibutuhkan ──
                response.setHeader("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), " +
                                                "payment=(), usb=(), interest-cohort=()");

                // ── Cache Control: cegah cache respons sensitif ──
                // Hanya apply ke endpoint API, bukan static asset
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");

                filterChain.doFilter(request, response);
        }
}