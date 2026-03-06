package mohammadnuridin.todolist.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
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
 * - Strict-Transport-Security : paksa HTTPS (hanya jika hsts-enabled=true)
 * - Content-Security-Policy : batasi sumber konten
 * - Referrer-Policy : kontrol info referrer
 * - Permissions-Policy : batasi fitur browser
 * - Cache-Control : cegah cache respons sensitif
 */
@Component
public class SecurityHeadersFilter extends OncePerRequestFilter {

        @Value("${app.security.headers.hsts-enabled:false}")
        private boolean hstsEnabled;

        @Value("${app.security.headers.csp:default-src 'self'}")
        private String cspPolicy;

        /**
         * Skip filter untuk path Actuator (dev & prod).
         *
         * Dev : base-path = /actuator → skip path mengandung "/actuator"
         * Prod : base-path = /internal/actuator → skip juga, karena mengandung
         * "/actuator"
         *
         * Filter ini adalah @Component sehingga dijalankan untuk SEMUA request
         * di port 8080. Management port 8082 memiliki servlet context sendiri
         * sehingga filter ini tidak apply ke sana.
         */
        @Override
        protected boolean shouldNotFilter(HttpServletRequest request) {
                String path = request.getRequestURI();
                return path.contains("/actuator");
        }

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

                // ── HSTS: paksa HTTPS — hanya aktif jika hsts-enabled=true (prod) ──
                // Di dev/test: false → header tidak di-set (aman untuk HTTP localhost)
                // Di prod: true → paksa HTTPS selama 1 tahun termasuk subdomain
                if (hstsEnabled) {
                        response.setHeader("Strict-Transport-Security",
                                        "max-age=31536000; includeSubDomains; preload");
                }

                // ── Content Security Policy — dari properties per profile ──
                response.setHeader("Content-Security-Policy", cspPolicy);

                // ── Referrer Policy ──
                response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");

                // ── Permissions Policy: matikan fitur browser yang tidak dibutuhkan ──
                response.setHeader("Permissions-Policy",
                                "camera=(), microphone=(), geolocation=(), " +
                                                "payment=(), usb=(), interest-cohort=()");

                // ── Cache Control: cegah cache respons sensitif ──
                response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
                response.setHeader("Pragma", "no-cache");
                response.setHeader("Expires", "0");

                filterChain.doFilter(request, response);
        }
}