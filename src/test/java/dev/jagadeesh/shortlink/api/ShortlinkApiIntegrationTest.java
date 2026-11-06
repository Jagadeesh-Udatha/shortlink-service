package dev.jagadeesh.shortlink.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import dev.jagadeesh.shortlink.domain.ClickEvent;
import dev.jagadeesh.shortlink.domain.Link;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end through MockMvc against a real (embedded) MongoDB. */
@SpringBootTest
@AutoConfigureMockMvc
class ShortlinkApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired MongoTemplate mongo;
    @Autowired ObjectMapper json;
    @Autowired MutableClock clock;

    @TestConfiguration
    static class Clocks {
        @Bean @Primary
        MutableClock testClock() {
            return new MutableClock(Instant.parse("2026-11-01T10:00:00Z"));
        }
    }

    @BeforeEach
    void clean() {
        mongo.dropCollection(Link.class);
        mongo.dropCollection(ClickEvent.class);
        clock.set(Instant.parse("2026-11-01T10:00:00Z"));
    }

    @Test
    void createRedirectAndCountClicks() throws Exception {
        String code = create("{\"url\":\"https://example.com/docs\"}").get("code").asText();
        assertThat(code).hasSize(7);

        mvc.perform(get("/" + code).header("Referer", "https://news.ycombinator.com/item?id=1"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://example.com/docs"))
                .andExpect(header().string("Cache-Control", "no-store"));

        mvc.perform(get("/api/links/" + code))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.clicks").value(1))
                .andExpect(jsonPath("$.shortUrl").value("http://sho.rt/" + code));
    }

    @Test
    void customAliasIsUniqueAndReservedWordsAreRefused() throws Exception {
        create("{\"url\":\"https://example.com\",\"alias\":\"spring-docs\"}");

        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://other.com\",\"alias\":\"spring-docs\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.title").value("Alias taken"));
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"https://other.com\",\"alias\":\"actuator\"}"))
                .andExpect(status().isConflict());
    }

    @Test
    void invalidInputGivesProblemDetails() throws Exception {
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"\",\"alias\":\"a!\",\"ttlDays\":0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.url").exists())
                .andExpect(jsonPath("$.errors.alias").exists())
                .andExpect(jsonPath("$.errors.ttlDays").exists());
        mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"url\":\"ftp://example.com\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Invalid URL"));
    }

    @Test
    void unknownIs404AndExpiredIs410() throws Exception {
        mvc.perform(get("/nope1234")).andExpect(status().isNotFound());

        String code = create("{\"url\":\"https://example.com\",\"ttlDays\":1}").get("code").asText();
        clock.advance(Duration.ofDays(2));   // past expiry, before MongoDB's TTL monitor deletes it
        mvc.perform(get("/" + code))
                .andExpect(status().isGone())
                .andExpect(jsonPath("$.title").value("Link expired"));
    }

    @Test
    void concurrentRedirectsNeverLoseAClick() throws Exception {
        String code = create("{\"url\":\"https://example.com\"}").get("code").asText();
        int requests = 200;
        try (ExecutorService pool = Executors.newFixedThreadPool(32)) {
            List<Callable<Integer>> calls = new ArrayList<>();
            for (int i = 0; i < requests; i++) {
                calls.add(() -> mvc.perform(get("/" + code)).andReturn().getResponse().getStatus());
            }
            for (Future<Integer> f : pool.invokeAll(calls)) {
                assertThat(f.get()).isEqualTo(302);
            }
        }
        assertThat(mongo.findById(code, Link.class).getClicks()).isEqualTo(requests);
        assertThat(mongo.count(new org.springframework.data.mongodb.core.query.Query(), ClickEvent.class)).isEqualTo(requests);
    }

    @Test
    void statsGroupClicksByDayAndReferrer() throws Exception {
        String code = create("{\"url\":\"https://example.com\"}").get("code").asText();
        mvc.perform(get("/" + code).header("Referer", "https://www.linkedin.com/feed"));
        mvc.perform(get("/" + code).header("Referer", "https://www.linkedin.com/in/x"));
        clock.advance(Duration.ofDays(1));
        mvc.perform(get("/" + code));

        mvc.perform(get("/api/links/" + code + "/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalClicks").value(3))
                .andExpect(jsonPath("$.daily.length()").value(2))
                .andExpect(jsonPath("$.daily[0].day").value("2026-11-01"))
                .andExpect(jsonPath("$.daily[0].clicks").value(2))
                .andExpect(jsonPath("$.topReferrers[0].referrer").value("www.linkedin.com"))
                .andExpect(jsonPath("$.topReferrers[0].clicks").value(2));
    }

    @Test
    void topListsMostClickedFirstAndDeleteRemovesHistory() throws Exception {
        String a = create("{\"url\":\"https://a.com\"}").get("code").asText();
        String b = create("{\"url\":\"https://b.com\"}").get("code").asText();
        mvc.perform(get("/" + b));
        mvc.perform(get("/" + b));
        mvc.perform(get("/" + a));

        mvc.perform(get("/api/links/top?limit=2"))
                .andExpect(jsonPath("$[0].code").value(b))
                .andExpect(jsonPath("$[1].code").value(a));

        mvc.perform(delete("/api/links/" + b)).andExpect(status().isNoContent());
        mvc.perform(get("/" + b)).andExpect(status().isNotFound());
        assertThat(mongo.count(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("code").is(b)), ClickEvent.class)).isZero();
    }

    private JsonNode create(String body) throws Exception {
        String res = mvc.perform(post("/api/links").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(res);
    }

    /** A clock tests can move forward. */
    static class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant now) { this.now = now; }
        void set(Instant i) { now = i; }
        void advance(Duration d) { now = now.plus(d); }
        @Override public ZoneId getZone() { return ZoneOffset.UTC; }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now; }
    }
}
