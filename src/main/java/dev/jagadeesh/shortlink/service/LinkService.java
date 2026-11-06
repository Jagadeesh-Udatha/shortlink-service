package dev.jagadeesh.shortlink.service;

import dev.jagadeesh.shortlink.config.ShortlinkProperties;
import dev.jagadeesh.shortlink.domain.ClickEvent;
import dev.jagadeesh.shortlink.domain.Link;
import dev.jagadeesh.shortlink.domain.LinkRepository;
import org.bson.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import static org.springframework.data.mongodb.core.aggregation.Aggregation.group;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.limit;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.match;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.project;
import static org.springframework.data.mongodb.core.aggregation.Aggregation.sort;
import static org.springframework.data.mongodb.core.query.Criteria.where;

@Service
public class LinkService {

    private static final Logger log = LoggerFactory.getLogger(LinkService.class);
    /** Paths the app itself serves, so they can never be taken as aliases. */
    private static final Set<String> RESERVED = Set.of("api", "actuator", "swagger-ui", "v3", "health", "admin");
    private static final int GENERATE_ATTEMPTS = 3;

    private final LinkRepository links;
    private final MongoTemplate mongo;
    private final CodeGenerator codes;
    private final UrlValidator urls;
    private final ShortlinkProperties props;
    private final Clock clock;

    public LinkService(LinkRepository links, MongoTemplate mongo, CodeGenerator codes, UrlValidator urls,
                       ShortlinkProperties props, Clock clock) {
        this.links = links;
        this.mongo = mongo;
        this.codes = codes;
        this.urls = urls;
        this.props = props;
        this.clock = clock;
    }

    public Link create(String longUrl, String alias, Integer ttlDays) {
        String target = urls.validate(longUrl, props.baseUrl()).toString();
        Instant now = clock.instant();
        int days = ttlDays == null ? props.defaultTtlDays() : Math.min(ttlDays, props.maxTtlDays());
        Instant expiresAt = now.plus(Duration.ofDays(days));

        if (alias != null && !alias.isBlank()) {
            if (RESERVED.contains(alias.toLowerCase(Locale.ROOT))) {
                throw new AliasTakenException(alias);
            }
            try {
                // insert (not save): fails on the unique _id instead of silently overwriting someone else's link
                return mongo.insert(new Link(alias, target, true, now, expiresAt));
            } catch (DuplicateKeyException e) {
                throw new AliasTakenException(alias);
            }
        }
        for (int attempt = 1; ; attempt++) {
            try {
                return mongo.insert(new Link(codes.next(), target, false, now, expiresAt));
            } catch (DuplicateKeyException e) {
                // only possible if someone took this exact code as a custom alias; take the next number
                if (attempt == GENERATE_ATTEMPTS) {
                    throw e;
                }
                log.warn("Generated code collided with an alias, retrying (attempt {})", attempt);
            }
        }
    }

    /**
     * Resolves a code for a redirect and counts the click in the same round trip:
     * findAndModify matches only unexpired links and applies $inc atomically, so concurrent
     * redirects never lose a count.
     */
    public Link resolve(String code, String referrerHost) {
        Instant now = clock.instant();
        Query live = Query.query(where("_id").is(code)
                .orOperator(where("expiresAt").is(null), where("expiresAt").gt(now)));
        Link link = mongo.findAndModify(live,
                new Update().inc("clicks", 1).set("lastClickedAt", now),
                FindAndModifyOptions.options().returnNew(true),
                Link.class);
        if (link == null) {
            // either it never existed or it expired but the TTL monitor has not removed it yet
            throw links.existsById(code) ? new LinkExpiredException(code) : new LinkNotFoundException(code);
        }
        mongo.insert(ClickEvent.of(code, now, referrerHost));
        return link;
    }

    public Link get(String code) {
        return links.findById(code).orElseThrow(() -> new LinkNotFoundException(code));
    }

    public void delete(String code) {
        if (!links.existsById(code)) {
            throw new LinkNotFoundException(code);
        }
        links.deleteById(code);
        mongo.remove(Query.query(where("code").is(code)), ClickEvent.class);
    }

    public List<Link> top(int limit) {
        return links.findAllByOrderByClicksDesc(PageRequest.of(0, limit));
    }

    public LinkStats stats(String code, int days) {
        Link link = get(code);
        Instant from = clock.instant().minus(Duration.ofDays(days));
        Criteria window = where("code").is(code).and("at").gte(from);

        Aggregation byDay = Aggregation.newAggregation(
                match(window),
                project().and("at").dateAsFormattedString("%Y-%m-%d").as("day"),
                group("day").count().as("clicks"),
                sort(Sort.Direction.ASC, "_id"));
        List<LinkStats.DailyClicks> daily = mongo.aggregate(byDay, ClickEvent.class, Document.class)
                .getMappedResults().stream()
                .map(d -> new LinkStats.DailyClicks(LocalDate.parse(d.getString("_id")), d.getInteger("clicks")))
                .toList();

        Aggregation byReferrer = Aggregation.newAggregation(
                match(window),
                group("referrerHost").count().as("clicks"),
                sort(Sort.Direction.DESC, "clicks"),
                limit(5));
        List<LinkStats.ReferrerClicks> referrers = mongo.aggregate(byReferrer, ClickEvent.class, Document.class)
                .getMappedResults().stream()
                .map(d -> new LinkStats.ReferrerClicks(
                        d.get("_id") == null ? "direct" : d.getString("_id"), d.getInteger("clicks")))
                .toList();

        return new LinkStats(code, link.getClicks(), daily, referrers);
    }
}
