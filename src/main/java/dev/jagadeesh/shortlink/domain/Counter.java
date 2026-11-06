package dev.jagadeesh.shortlink.domain;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/** A named sequence, advanced with a single atomic findAndModify($inc). */
@Document("counters")
public record Counter(@Id String id, long seq) {
}
