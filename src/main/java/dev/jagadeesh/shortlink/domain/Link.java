package dev.jagadeesh.shortlink.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A short link. The short code is the document id, so a custom alias is unique by construction
 * and a lookup by code is a primary-key read.
 */
@Document("links")
public class Link {

    @Id
    private String code;
    private String longUrl;
    private boolean customAlias;
    private Instant createdAt;

    /** MongoDB's TTL monitor deletes the document once this time has passed (it runs about once a minute). */
    @Indexed(expireAfter = "0s")
    private Instant expiresAt;

    /** Incremented atomically with $inc on every redirect, never read-modify-written. */
    @Indexed
    private long clicks;
    private Instant lastClickedAt;

    @Version
    private Long version;

    protected Link() {
    }

    public Link(String code, String longUrl, boolean customAlias, Instant createdAt, Instant expiresAt) {
        this.code = code;
        this.longUrl = longUrl;
        this.customAlias = customAlias;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isExpired(Instant now) {
        return expiresAt != null && !expiresAt.isAfter(now);
    }

    public String getCode() { return code; }
    public String getLongUrl() { return longUrl; }
    public boolean isCustomAlias() { return customAlias; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getExpiresAt() { return expiresAt; }
    public long getClicks() { return clicks; }
    public Instant getLastClickedAt() { return lastClickedAt; }
    public Long getVersion() { return version; }
}
