package com.csob.ca.api.security;

import org.springframework.context.annotation.Configuration;

/**
 * Authentication, authorisation, and 4-eyes policy for CA endpoints.
 * Reviewer identity is taken from the authenticated principal; controllers
 * MUST NOT accept reviewer usernames from request bodies.
 */
@Configuration
public class SecurityConfig {
    // TODO: SecurityFilterChain bean once auth mechanism is chosen for v1.
}
