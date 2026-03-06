package mohammadnuridin.todolist.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import mohammadnuridin.todolist.core.security.JwtAuthFilter;

import org.springframework.boot.actuate.autoconfigure.security.servlet.EndpointRequest;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
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
import org.springframework.security.web.access.expression.WebExpressionAuthorizationManager;
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
                                .headers(AbstractHttpConfigurer::disable)
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .sessionManagement(session -> session
                                                .sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                                .exceptionHandling(ex -> ex
                                                .authenticationEntryPoint((request, response, authException) -> {
                                                        response.setStatus(HttpStatus.UNAUTHORIZED.value());
                                                        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                                                        response.getWriter().write(
                                                                        objectMapper.writeValueAsString(Map.of(
                                                                                        "success", false,
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

                                                // Actuator di port 8080 hanya ada di dev profile
                                                // (prod: actuator ada di port 8082, chain ini tidak berlaku)
                                                .requestMatchers("/actuator/**")
                                                .access(new WebExpressionAuthorizationManager(
                                                                "hasIpAddress('127.0.0.1') or hasIpAddress('::1') " +
                                                                                "or hasIpAddress('172.16.0.0/12') " +
                                                                                "or hasIpAddress('10.0.0.0/8')"))

                                                .anyRequest().authenticated())

                                .addFilterBefore(securityHeadersFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(rateLimiterFilter, UsernamePasswordAuthenticationFilter.class)
                                .addFilterBefore(jwtAuthFilter, UsernamePasswordAuthenticationFilter.class);

                return http.build();
        }

        /**
         * SecurityFilterChain untuk management port 8082.
         * Hanya aktif saat management.server.port di-set (prod profile).
         *
         * Strategi:
         * - /internal/actuator/health → permitAll (Docker healthcheck butuh ini)
         * - /internal/actuator/** → batasi IP (hanya Prometheus & internal)
         *
         * Kenapa health permitAll di sini?
         * Docker healthcheck berjalan dari dalam container.
         * Meski IP-nya 127.0.0.1, Spring Security di management port
         * kadang tidak resolve IP dengan benar karena servlet context berbeda.
         * Health endpoint tidak mengekspos data sensitif — aman untuk dibuka.
         */
        @Bean
        @ConditionalOnProperty(name = "management.server.port")
        public SecurityFilterChain managementSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                .securityMatcher(EndpointRequest.toAnyEndpoint())
                                .authorizeHttpRequests(auth -> auth
                                                // Health endpoint: permitAll
                                                // Dibutuhkan oleh Docker healthcheck & load balancer
                                                .requestMatchers(EndpointRequest.to(HealthEndpoint.class))
                                                .permitAll()

                                                // Endpoint lain (prometheus, metrics, info):
                                                // hanya dari IP internal
                                                .requestMatchers(EndpointRequest.toAnyEndpoint())
                                                .access(new WebExpressionAuthorizationManager(
                                                                "hasIpAddress('127.0.0.1') or hasIpAddress('::1') " +
                                                                                "or hasIpAddress('172.16.0.0/12') " +
                                                                                "or hasIpAddress('10.0.0.0/8')"))

                                                .anyRequest().denyAll())
                                .csrf(AbstractHttpConfigurer::disable)
                                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
                return http.build();
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration config = new CorsConfiguration();
                config.setAllowedOrigins(List.of(
                                "http://localhost:3000",
                                "http://localhost:5173"));
                config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                config.setAllowedHeaders(List.of("*"));
                config.setAllowCredentials(true);
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