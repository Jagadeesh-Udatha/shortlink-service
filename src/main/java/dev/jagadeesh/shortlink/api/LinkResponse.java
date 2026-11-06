package dev.jagadeesh.shortlink.api;

import dev.jagadeesh.shortlink.domain.Link;

import java.time.Instant;

public record LinkResponse(String code, String shortUrl, String longUrl, boolean customAlias,
                           Instant createdAt, Instant expiresAt, long clicks, Instant lastClickedAt) {

    static LinkResponse of(Link l, String baseUrl) {
        return new LinkResponse(l.getCode(), baseUrl + "/" + l.getCode(), l.getLongUrl(), l.isCustomAlias(),
                l.getCreatedAt(), l.getExpiresAt(), l.getClicks(), l.getLastClickedAt());
    }
}
