package com.csob.ca.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

/**
 * Spring Boot entry point. THE ONLY module permitted to carry
 * @SpringBootApplication and Spring Boot starters.
 *
 * Scanning is explicit so that controllers cannot accidentally import
 * persistence types: @EntityScan and @EnableJpaRepositories are scoped
 * to com.csob.ca.persistence.* only.
 */
@SpringBootApplication(scanBasePackages = {
        "com.csob.ca.api",
        "com.csob.ca.orchestration",
        "com.csob.ca.checklist",
        "com.csob.ca.tools",
        "com.csob.ca.ai",
        "com.csob.ca.validation",
        "com.csob.ca.persistence"
})
@EntityScan(basePackages = "com.csob.ca.persistence.entity")
@EnableJpaRepositories(basePackages = {
        "com.csob.ca.persistence.repository",
        "com.csob.ca.persistence.audit"
})
@ComponentScan
public class CaApplication {

    public static void main(String[] args) {
        SpringApplication.run(CaApplication.class, args);
    }
}
