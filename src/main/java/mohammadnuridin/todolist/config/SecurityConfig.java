package mohammadnuridin.todolist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.core.security.JwtAuthFilter;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;
import java.util.Map;

import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

        private final JwtAuthFilter jwtAuthFilter;
        private final SecurityHeadersFilter securityHeadersFilter;
        private final RateLimiterFilter rateLimiterFilter;
        private final ObjectMapper objectMapper;

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
                http
                                .csrf(AbstractHttpConfigurer::disable)

                                // Nonaktifkan default security headers Spring — kita atur sendiri via
                                // SecurityHeadersFilter
                                .headers(AbstractHttpConfigurer::disable)

                                // CORS — penting untuk Web agar cookie bisa dikirim lintas origin
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))

                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                                        response.getWriter().write(
                                                                        objectMapper.writeValueAsString(
                                                                                        Map.of(
                                                                                                        "success",
                                                                                                        false,
                                                                                                        "message",
                                                                                                        "Unauthorized — token missing or invalid")));
                                                }))

                                .authorizeHttpRequests(auth -> auth
                                                .requestMatchers(
                                                                "/",
                                                                "/greet",
                                                                "/auth/login",
                                                                "/users/register",
                                                                "/auth/refresh")
                                                .permitAll()
                                                .anyRequest().authenticated())

                                // ── Urutan filter (dari atas ke bawah = awal ke akhir) ──
                                //
                                // 1. SecurityHeadersFilter — inject security headers ke setiap response
                                // 2. RateLimiterFilter — cek rate limit sebelum request diproses lebih jauh
                                // 3. JwtAuthFilter — validasi JWT token
                                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(rateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * CORS config untuk Web browser.
         * withCredentials=true di frontend membutuhkan:
         * - allowedOrigins TIDAK boleh "*"
         * - allowCredentials(true)
         */
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(
                                "http://localhost:3000", // React dev
                                "http://localhost:5173" // Vite dev
                ));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true); // wajib agar cookie terkirim
                config.setMaxAge(3600L);

                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", config);
                return source;
        }

        @Bean
        public PasswordEncoder passwordEncoder() {
                return new BCryptPasswordEncoder();
        }
}