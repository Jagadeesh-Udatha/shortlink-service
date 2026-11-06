package dev.jagadeesh.shortlink.config;

import dev.jagadeesh.shortlink.domain.ClickEvent;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** Indexes whose options come from configuration, so they cannot be declared with annotations. */
@Component
public class MongoIndexes {

    private final MongoTemplate mongo;
    private final ShortlinkProperties props;

    public MongoIndexes(MongoTemplate mongo, ShortlinkProperties props) {
        this.mongo = mongo;
        this.props = props;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void ensureClickRetention() {
        mongo.indexOps(ClickEvent.class).ensureIndex(new Index("at", Sort.Direction.ASC)
                .named("click_ttl")
                .expire(Duration.ofDays(props.clickRetentionDays())));
    }
}
