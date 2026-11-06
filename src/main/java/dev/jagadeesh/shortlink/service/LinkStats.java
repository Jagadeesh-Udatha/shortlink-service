package dev.jagadeesh.shortlink.service;

import java.time.LocalDate;
import java.util.List;

/** Click analytics for one link, built with MongoDB aggregation pipelines. */
public record LinkStats(String code, long totalClicks, List<DailyClicks> daily, List<ReferrerClicks> topReferrers) {

    public record DailyClicks(LocalDate day, long clicks) {
    }

    public record ReferrerClicks(String referrer, long clicks) {
    }
}
