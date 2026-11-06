package dev.jagadeesh.shortlink.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

@Configuration
public class ClockConfig {

    /** Injected instead of calling Instant.now() so expiry logic can be tested with a fixed clock. */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
