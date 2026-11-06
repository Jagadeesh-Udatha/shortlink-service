package dev.jagadeesh.shortlink.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * One redirect. Kept for the analytics endpoints and removed by a TTL index after the retention period
 * (the index is created in {@link dev.jagadeesh.shortlink.config.MongoIndexes} because its lifetime is configurable).
 */
@Document("clicks")
@CompoundIndex(name = "code_at", def = "{'code': 1, 'at': -1}")
public record ClickEvent(@Id String id, String code, Instant at, String referrerHost) {

    public static ClickEvent of(String code, Instant at, String referrerHost) {
        return new ClickEvent(null, code, at, referrerHost);
    }
}
