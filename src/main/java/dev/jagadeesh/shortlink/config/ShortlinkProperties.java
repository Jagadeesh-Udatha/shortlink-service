package dev.jagadeesh.shortlink.config;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Settings under {@code shortlink.*}.
 *
 * @param baseUrl            public origin used to build short URLs, e.g. https://sho.rt
 * @param defaultTtlDays     lifetime of a link when the caller does not ask for one
 * @param maxTtlDays         upper bound a caller may request
 * @param clickRetentionDays how long raw click events are kept (MongoDB TTL index)
 */
@Validated
@ConfigurationProperties(prefix = "shortlink")
public record ShortlinkProperties(
        @NotBlank String baseUrl,
        @Positive int defaultTtlDays,
        @Positive @Max(3650) int maxTtlDays,
        @Positive int clickRetentionDays) {
}
