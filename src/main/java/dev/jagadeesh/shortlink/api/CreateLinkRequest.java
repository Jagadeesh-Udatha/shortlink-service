package dev.jagadeesh.shortlink.api;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record CreateLinkRequest(
        @Schema(example = "https://docs.spring.io/spring-boot/reference/data/nosql.html")
        @NotBlank @Size(max = 2048) String url,

        @Schema(description = "Optional custom code, 4-32 letters, digits, '-' or '_'", example = "spring-mongo")
        @Pattern(regexp = "^[A-Za-z0-9_-]{4,32}$", message = "must be 4-32 letters, digits, '-' or '_'") String alias,

        @Schema(description = "Days until the link expires (default 90)", example = "30")
        @Positive @Max(3650) Integer ttlDays) {
}
