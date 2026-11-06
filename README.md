# Shortlink Service

A production-style **URL shortener** built with **Spring Boot 3** and **MongoDB**: collision-free short codes, custom aliases, link expiry, atomic click counting and click analytics.

Java 21 · Spring Boot 3.4 · Spring Data MongoDB · Bean Validation · springdoc OpenAPI · JUnit 5 · Embedded MongoDB · Docker · GitHub Actions

## API

| Method | Path | What it does |
|---|---|---|
| `POST` | `/api/links` | Shorten a URL (`url`, optional `alias`, optional `ttlDays`) → `201 Created` |
| `GET` | `/{code}` | `302` redirect to the original URL and count the click |
| `GET` | `/api/links/{code}` | Link details and total clicks |
| `GET` | `/api/links/{code}/stats?days=30` | Clicks per day and top referrers |
| `GET` | `/api/links/top?limit=10` | Most clicked links |
| `DELETE` | `/api/links/{code}` | Delete a link and its click history |

Swagger UI: `http://localhost:8080/swagger-ui.html` · Health: `/actuator/health`

Errors are returned as RFC 7807 `application/problem+json`: `400` validation, `404` unknown code, `409` alias taken, `410` expired.

## Design

```
POST /api/links ──▶ UrlValidator ──▶ CodeGenerator ──▶ insert into links (_id = code)
                                        │
                                        └─ counters: findAndModify {$inc: seq}  →  seq × M mod 62^7  →  Base62 (7 chars)

GET /{code} ──▶ findAndModify(links, {_id: code, expiresAt > now}, {$inc: clicks})  ──▶ 302 Location
                       └─▶ insert into clicks {code, at, referrerHost}   (TTL: 90 days)

GET /stats  ──▶ aggregation: $match code+window → $group by day / by referrer → $sort
```

| Concern | How it is handled |
|---|---|
| **Unique codes across many instances** | A MongoDB counter advanced with one atomic `findAndModify($inc)`; no locks, no retries in the normal path |
| **Codes that can't be enumerated** | Each sequence number is multiplied by a prime coprime with 62⁷ (mod 62⁷). The mapping is a bijection, so it can never collide, but `1000` and `1001` give unrelated codes |
| **Custom aliases** | The code is the document `_id`, so uniqueness is enforced by MongoDB; `insert` (not `save`) turns a clash into `409` instead of overwriting. Paths the app serves (`api`, `actuator`, ...) are reserved |
| **No lost clicks under load** | The redirect matches and increments in a single `findAndModify` with `$inc`; there is no read-modify-write. A test fires 200 concurrent redirects and expects exactly 200 |
| **Expiry** | `expiresAt` has a TTL index, so MongoDB deletes expired links. The TTL monitor only runs every ~60 s, so the redirect query also checks `expiresAt > now` and returns `410 Gone` in that gap |
| **Analytics without unbounded growth** | Raw click events go to their own collection with a compound `(code, at)` index for the stats queries and a TTL index for retention |
| **Every visit counted** | `302` with `Cache-Control: no-store` rather than a cacheable `301` |
| **Safe targets** | Only absolute `http`/`https` URLs with a host; links pointing back at the shortener are refused |
| **Testable time** | A `Clock` bean is injected everywhere, so expiry and daily stats are tested with a controllable clock |

## Run it

```bash
docker compose up --build
curl -s -X POST localhost:8080/api/links -H 'Content-Type: application/json' \
     -d '{"url":"https://spring.io/projects/spring-data-mongodb","alias":"spring-mongo","ttlDays":30}'
curl -i localhost:8080/spring-mongo
curl -s localhost:8080/api/links/spring-mongo/stats
```

Without Docker: start MongoDB on `localhost:27017`, then `mvn spring-boot:run`.

| Variable | Default |
|---|---|
| `MONGO_HOST` / `MONGO_PORT` / `MONGO_DB` | `localhost` / `27017` / `shortlink` |
| `SHORTLINK_BASE_URL` | `http://localhost:8080` |

## Tests

```bash
mvn verify
```

18 tests. The integration tests run the full HTTP flow through MockMvc against a real embedded MongoDB (no Docker needed): create, redirect, alias conflicts, validation errors, expiry (`410`), 200 concurrent redirects, per-day and per-referrer stats, top links and delete.
