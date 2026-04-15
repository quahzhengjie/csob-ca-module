package com.csob.ca.api.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Minimal v1 Spring Security configuration — DEV/LOCAL ONLY.
 *
 * This config is deliberately open for {@code /api/ca/**} so the persistence
 * + replay endpoints can be exercised from curl / smoke tests before a real
 * auth mechanism is chosen. Production deployments MUST replace this with:
 *   - authenticated principal extraction (reviewer identity on CaPack)
 *   - role / permission gating for POST vs replay
 *   - CSRF protection where applicable
 *   - 4-eyes enforcement for sign-off endpoints
 *
 * The class is annotated as a v1 placeholder so it's loud in code review.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain caPackFilterChain(HttpSecurity http) throws Exception {
        // NOTE: server.servlet.context-path=/api is stripped before matchers run,
        // so the patterns below are relative to the context root.
        http
            .csrf(csrf -> csrf.disable())  // stateless JSON API; CSRF n/a for v1
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/ca/**").permitAll()        // dev open for all CA endpoints
                .requestMatchers("/actuator/**").permitAll()  // let health/info through
                .requestMatchers("/error").permitAll()        // serve real error bodies, not 403
                .anyRequest().denyAll()                       // everything else is locked
            );
        return http.build();
    }
}
