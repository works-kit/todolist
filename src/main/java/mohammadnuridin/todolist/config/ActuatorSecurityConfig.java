package mohammadnuridin.todolist.config;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.actuate.info.InfoEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

import java.util.Map;

/**
 * Security filter chain KHUSUS untuk Actuator endpoints.
 *
 * KENAPA FILE TERPISAH?
 * ─────────────────────
 * Spring Boot 3.x + server.servlet.context-path=/api membuat Actuator
 * beroperasi di luar context-path aplikasi. Akibatnya:
 *
 * App endpoint → http://localhost:8081/api/users/register
 * Actuator → http://localhost:8081/actuator/prometheus ← TANPA /api
 *
 * SecurityConfig utama hanya meng-handle request yang masuk ke /api/**.
 * Request ke /actuator/** tidak masuk filter chain utama, sehingga
 * Actuator dianggap "No static resource" dan jatuh ke ErrorController (500).
 *
 * Solusi: buat SecurityFilterChain terpisah dengan @Order lebih tinggi (1)
 * yang khusus meng-handle path /actuator/** menggunakan EndpointRequest
 * bawaan Spring Boot Actuator.
 *
 * URUTAN FILTER CHAIN:
 * @Order(1) → ActuatorSecurityConfig (handle /actuator/**)
 * @Order(2) → SecurityConfig utama (handle semua request lainnya)
 */
@Configuration
@RequiredArgsConstructor
public class ActuatorSecurityConfig {

    private final ObjectMapper objectMapper;

    @Bean
    @Order(1) // Harus lebih tinggi (angka lebih kecil) dari SecurityConfig utama
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // Hanya berlaku untuk request ke Actuator endpoints
                .securityMatcher(EndpointRequest.toAnyEndpoint())

                .csrf(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                .authorizeHttpRequests(auth -> auth
                        // health & info: boleh diakses publik (untuk load balancer / uptime check)
                        .requestMatchers(EndpointRequest.to(HealthEndpoint.class, InfoEndpoint.class))
                        .permitAll()

                        // Semua endpoint actuator lainnya (prometheus, metrics, loggers, dll.)
                        // hanya dari localhost — blokir dari luar
                        .anyRequest()
                        .access(new org.springframework.security.web.access.expression.WebExpressionAuthorizationManager(
                                "hasIpAddress('127.0.0.1') or hasIpAddress('::1')")))

                // Custom 401 response jika akses dari IP asing
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((request, response, authException) -> {
                            response.setStatus(HttpStatus.UNAUTHORIZED.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Map.of(
                                            "code", 401,
                                            "status", "failed",
                                            "errors", "Actuator access denied — internal use only")));
                        })
                        .accessDeniedHandler((request, response, accessDeniedException) -> {
                            response.setStatus(HttpStatus.FORBIDDEN.value());
                            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                            response.getWriter().write(
                                    objectMapper.writeValueAsString(Map.of(
                                            "code", 403,
                                            "status", "failed",
                                            "errors", "Actuator access forbidden — internal use only")));
                        }));

        return http.build();
    }
}