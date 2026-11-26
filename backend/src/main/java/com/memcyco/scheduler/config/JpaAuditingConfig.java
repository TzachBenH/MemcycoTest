package com.memcyco.scheduler.config;

import com.memcyco.scheduler.security.CurrentUserProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

import java.util.Optional;

/** Fills {@code created_at/updated_at/created_by/updated_by} automatically. */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    @Bean
    AuditorAware<String> auditorAware(CurrentUserProvider currentUserProvider) {
        return () -> Optional.of(currentUserProvider.currentUsername());
    }
}
