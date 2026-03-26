# freshmarkt-sync

A Spring Boot service that synchronizes product pricing and availability data from the FreshMarkt partner API into a local data store. The service is designed to run periodically via a configurable scheduler and handles the messy reality of third-party API integrations defensively.

---

## Tech Stack

- Java 21
- Spring Boot 3.5.x
- Spring Data JPA + H2 (in-memory)
- Maven
- Lombok

---

## Running the Service
```bash
mvn spring-boot:run
```

## Running the Tests
```bash
mvn test
```

---

## Architecture

The service follows a clear separation of concerns, organized by feature rather than by type:
```
client/         → FreshMarkt HTTP client, DTOs, config
domain/         → JPA entities and repositories  
sync/           → Orchestration, mapping, persistence, retry, state
```

### Call Chain
```
@Scheduled sync()                     → entry point, orchestrates the full sync
    └── syncAllPages()                → handles pagination loop
            └── fetchWithRetry()     → handles retry logic
                    └── fetchProducts()   → makes the HTTP call
```

---

## Design Decisions

### Defensive Deserialization

The FreshMarkt API is a third-party system outside our control. The spec itself warns:

> *"We recently migrated our backend and some data formatting may vary between older and newer product entries."*

All DTOs are annotated with `@JsonIgnoreProperties(ignoreUnknown = true)` as a deliberate defensive tactic — we accept what we know about and silently ignore what we don't. This prevents the sync from crashing if FreshMarkt adds new fields without notice.

The API schema is intentionally loose in places — `price` has no declared type and can arrive as either a number (`2.95`) or a string (`"4.80"`). `availability` can be null. The `lastUpdated` field appears in two different formats in the same response (`ISO 8601` and `dd.MM.yyyy`). All of these are handled explicitly in the mapping layer rather than relying on the API to be consistent.

### Endpoints Used

Only the `GET /products` endpoint is used. The `GET /products/{productId}` and `GET /stores` endpoints were intentionally excluded:

- The list endpoint already contains everything needed for price and availability sync
- Calling `/products/{productId}` for every changed item would severely impact sync performance and hit rate limits too quickly
- The extra fields available via the detail endpoint (`brand`, `unit`, `pricePerUnit`) are static metadata that changes infrequently — a future improvement would be a secondary metadata sync running at a lower frequency to fetch this data separately

This version prioritizes high-frequency data (price and stock) which is the core use case.

### Incremental Sync

The service uses `updatedSince` to perform incremental syncs after the first full run. Rather than deriving the cursor from FreshMarkt's own `lastUpdated` field, we persist our own `lastSyncedAt` timestamp in a dedicated `SyncState` table.

Using FreshMarkt's timestamps as a cursor is unreliable because:
- The field appears in two different date formats in the same response
- It is a product-level field, not a sync-level concept
- If a sync crashes halfway through, the cursor could advance incorrectly

By controlling our own cursor with `Instant.now()` (always UTC), we guarantee that:
- The cursor only advances after **all pages** have been successfully processed
- If a sync fails mid-way, the next run starts from the last fully successful sync — nothing is missed
- This provides **at-least-once delivery** semantics

`Instant` was used throughout rather than `OffsetDateTime` since all timestamps in the API are UTC. Timezone handling would be a future improvement if multi-timezone support were required.

### Transaction Strategy

Rather than wrapping the entire sync in a single global transaction, each page is persisted in its own `@Transactional` boundary via `FreshMarktProductPersistenceService`. This approach:

- Keeps the service memory-efficient — we never hold thousands of products in memory at once
- Avoids holding database locks during external network calls
- Allows partial progress to be committed — if page 47 of 97 fails, pages 1–46 are already saved

Consistency is maintained by only updating `SyncState` after all pages complete successfully. The worst case is re-processing some pages on the next run, which is safe because all saves are upserts.

> **Note:** `@Transactional` on `processPage` lives in a separate `@Service` class (`FreshMarktProductPersistenceService`) to avoid Spring's proxy pitfall — calling a `@Transactional` method from within the same class bypasses the proxy and the transaction is never started.

### Composite Primary Key

Products are uniquely identified by the combination of `productId` + `storeId` as specified in the API — the same product can have different prices and availability across stores.

A composite primary key was enforced at the schema level using `@EmbeddedId` rather than handling lookups manually in the service layer with `findByProductIdAndStoreId`. This approach:

- Enforces data integrity at the database level
- Allows JPA to correctly identify existing records for upsert via `saveAll`
- Improves performance by letting JPA batch updates and inserts efficiently

### Manual Mapping

Manual mapping was chosen over MapStruct to keep the defensive parsing logic explicit and visible. The mapping layer is where all of FreshMarkt's data inconsistencies are normalized:

- `price` — handled via pattern matching on `Object` type, supports `Number`, `String`, and falls back to `null` with a warning log for unexpected types. `BigDecimal` is used throughout — never `double` — to avoid floating point precision issues with monetary values
- `availability` — null or unrecognised values are mapped to `UNKNOWN` rather than crashing
- `lastUpdated` — a `DateTimeFormatter` handles both `ISO 8601` and `dd.MM.yyyy` formats explicitly. A regex-based fallback could handle further variations but is out of scope here

In a larger codebase with many entities, MapStruct would be the better choice.

The mapper handles all known data inconsistencies from the spec. An unmapped exception here would indicate something truly unexpected — I'd rather fail loudly and fix it than silently skip products

### Rate Limit Handling

The API enforces a limit of 60 requests per minute. This is handled at two levels:

**Proactive** — a fixed delay of ~1,100ms between page fetches keeps the request rate at ~54 req/min, safely under the limit by design.

**Reactive** — a `429 Too Many Requests` response is caught explicitly, the `Retry-After` header is read and honoured, and the request is retried up to a configurable maximum. This handles edge cases where the proactive throttle is insufficient (e.g. clock drift, shared API keys).

`500` server errors are also retried with a fixed backoff. `400` and `401` errors are not retried — a bad request or invalid API key will not fix itself and requires developer intervention.

The scheduler catches all exceptions from the sync and does **not** rethrow them — a failed sync should not kill the scheduler. The next scheduled run will attempt again from the last successful sync state. The cron expression is externalised to `application.yaml` so it can be configured per environment.

### Lombok

Lombok is used throughout to reduce boilerplate — `@Slf4j` for logging, `@RequiredArgsConstructor` for constructor injection, and `@NoArgsConstructor(access = AccessLevel.PROTECTED)` on JPA entities to satisfy the JPA spec without exposing a public no-args constructor.

`@Data` is intentionally avoided on JPA entities as it generates `equals`/`hashCode` based on all fields which causes issues with Hibernate's dirty checking and lazy loading proxies.

### Entity Fields

All fields available from the `GET /products` list endpoint are persisted. Storage is cheap and dropping data that later turns out to be needed is costly. Fields only available via the detail endpoint (`brand`, `unit`, `pricePerUnit`) are excluded since retrieving them would require one additional HTTP call per product — incompatible with the rate limit and sync performance requirements.

---

## Edge Cases Handled

- `price` arriving as a number or a string
- `price` arriving as null or an unparseable value — persisted as null, sync continues
- `availability` arriving as null or an unrecognised value — mapped to `UNKNOWN`
- `lastUpdated` in ISO 8601 or European (`dd.MM.yyyy`) format
- Same `productId` across multiple stores — treated as distinct records via composite key
- Empty response (no products updated since last sync) — handled gracefully, state advances
- Mid-sync failure — sync state not advanced, next run retries from last successful point
- Products deleted from FreshMarkt — will not appear in `updatedSince` responses and will remain in the local store with their last known state. A full reconciliation sync comparing local vs remote would be needed to detect deletions — out of scope for this version

---

## Configuration
```yaml
freshmarkt:
  api:
    base-url: https://api.freshmarkt.fake/v1
    key: ${FRESHMARKT_API_KEY}    # injected via environment variable
  sync:
    cron: "0 */15 * * * *"        # configurable per environment
```

The API key is never hardcoded — it is injected via the `FRESHMARKT_API_KEY` environment variable. In production this would be managed via a secrets manager (e.g. AWS Secrets Manager).

---

## What I Would Improve Given More Time

- **Circuit breaker** — add Resilience4j to prevent cascading failures if FreshMarkt becomes unavailable for an extended period, with alerting on the open state
- **Alerting** — notify on-call if the sync fails more than N consecutive times, or if rate limiting persists for more than a configurable threshold (e.g. 1 hour)
- **Deletion handling** — periodic full reconciliation sync to detect and soft-delete products removed from FreshMarkt
- **Secondary metadata sync** — a lower-frequency sync for static fields (`brand`, `unit`, `pricePerUnit`) available via the detail endpoint
- **Exponential backoff** — replace the fixed 5s backoff on 5xx errors with exponential backoff and jitter
- **Regex date fallback** — extend `parseLastUpdated` to handle further date format variations beyond the two currently known
- **JDBC batch tuning** — configure `hibernate.jdbc.batch_size` for higher throughput on large syncs
- **Observability** — structured logging with correlation IDs, metrics on sync duration and product counts per run